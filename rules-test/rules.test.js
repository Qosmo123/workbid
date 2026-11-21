// Tests for ../firestore.rules against the Firestore emulator.
// Run from this folder:  npm install  then  npm test
// The writes below mirror exactly what the Android app sends (ui/AppViewModel.kt).

import { test, before, beforeEach, after } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  initializeTestEnvironment, assertSucceeds, assertFails,
} from '@firebase/rules-unit-testing';
import {
  doc, setDoc, addDoc, updateDoc, deleteDoc, getDoc, getDocs, collection, writeBatch, query, where,
} from 'firebase/firestore';

let env;
const HOME = 'home1', PRO = 'pro1', PRO2 = 'pro2', STRANGER = 'x1';
const now = () => Date.now();

const db = (uid) => env.authenticatedContext(uid).firestore();
const anon = () => env.unauthenticatedContext().firestore();

const jobData = (over = {}) => ({
  ref: 'WB-12345', title: 'Fix sticking patio door', category: 'Carpentry', area: 'Fairfield, Victoria',
  description: 'Door sticks at the top.', budgetMin: 100, budgetMax: 300, timing: 'Flexible',
  preferDate: '2026-12-01', status: 'open', postedBy: HOME, postedByName: 'Hana',
  createdAt: now(), acceptedBidId: '', contractorId: '', ...over,
});

const bidData = (jobId, uid = PRO, name = 'Pat', over = {}) => ({
  company: 'Pat Carpentry', amount: 250, days: 1, availableDate: '2026-12-02', window: '8–10 AM',
  message: 'Can do', createdAt: now(), jobId, jobOwner: HOME, contractorId: uid, contractorName: name, ...over,
});

const acceptData = (bidId, b) => ({
  status: 'scheduled', acceptedBidId: bidId, contractorId: b.contractorId, contractorName: b.contractorName,
  company: b.company, price: b.amount, scheduledDate: b.availableDate, window: b.window,
});

// Seed data bypassing the rules.
async function seed(fn) {
  await env.withSecurityRulesDisabled(async (ctx) => fn(ctx.firestore()));
}

async function seedUsers() {
  await seed(async (f) => {
    await setDoc(doc(f, 'users', HOME), { name: 'Hana', createdAt: 1 });
    await setDoc(doc(f, 'users', PRO), { name: 'Pat', createdAt: 1 });
    await setDoc(doc(f, 'users', PRO2), { name: 'Quinn', createdAt: 1 });
    await setDoc(doc(f, 'users', STRANGER), { name: 'Sam', createdAt: 1 });
  });
}

async function seedOpenJobWithBids() {
  const b1 = bidData('j1');
  const b2 = bidData('j1', PRO2, 'Quinn', { company: 'Quinn Co', amount: 300 });
  await seed(async (f) => {
    await setDoc(doc(f, 'jobs', 'j1'), jobData());
    await setDoc(doc(f, 'bids', `j1_${PRO}`), b1);
    await setDoc(doc(f, 'bids', `j1_${PRO2}`), b2);
  });
  return { b1, b2 };
}

async function seedBookedJob(status = 'scheduled') {
  const { b1 } = await seedOpenJobWithBids();
  await seed(async (f) => {
    await setDoc(doc(f, 'jobs', 'j1'), { ...jobData(), ...acceptData(`j1_${PRO}`, b1), status });
  });
  return b1;
}

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-workbid',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});
beforeEach(async () => { await env.clearFirestore(); await seedUsers(); });
after(async () => { await env.cleanup(); });

// ---------- users ----------
test('users: signed-out users read nothing', async () => {
  await assertFails(getDoc(doc(anon(), 'users', HOME)));
});
test('users: write own profile with valid name only', async () => {
  await assertSucceeds(setDoc(doc(db(HOME), 'users', HOME), { name: 'Hana B', createdAt: now() }));
  await assertFails(setDoc(doc(db(HOME), 'users', PRO), { name: 'Evil', createdAt: now() }));
  await assertFails(setDoc(doc(db(HOME), 'users', HOME), { name: '', createdAt: now() }));
  await assertFails(setDoc(doc(db(HOME), 'users', HOME), { name: 'Hana', createdAt: now(), admin: true }));
});

// ---------- jobs: create ----------
test('jobs: homeowner posts a valid job', async () => {
  await assertSucceeds(addDoc(collection(db(HOME), 'jobs'), jobData()));
});
test('jobs: cannot post as someone else, with a fake name, or already booked', async () => {
  await assertFails(addDoc(collection(db(HOME), 'jobs'), jobData({ postedBy: PRO })));
  await assertFails(addDoc(collection(db(HOME), 'jobs'), jobData({ postedByName: 'Workbid Support' })));
  await assertFails(addDoc(collection(db(HOME), 'jobs'), jobData({ status: 'scheduled' })));
  await assertFails(addDoc(collection(db(HOME), 'jobs'), jobData({ contractorId: PRO })));
});
test('jobs: field validation (B6)', async () => {
  const bad = [
    { title: '' }, { description: 'x'.repeat(2001) }, { budgetMin: -5 }, { budgetMin: 500, budgetMax: 100 },
    { category: 'Hacking' }, { preferDate: 'tomorrow' }, { extra: 1 },
  ];
  for (const over of bad) await assertFails(addDoc(collection(db(HOME), 'jobs'), jobData(over)));
});

// ---------- bids (B5) ----------
test('bids: contractor bids once, at the fixed ID', async () => {
  await seed(async (f) => setDoc(doc(f, 'jobs', 'j1'), jobData()));
  await assertSucceeds(setDoc(doc(db(PRO), 'bids', `j1_${PRO}`), bidData('j1')));
  await assertFails(addDoc(collection(db(PRO), 'bids'), bidData('j1')));                 // random ID = 2nd bid
  await assertFails(setDoc(doc(db(PRO), 'bids', `j1_${PRO2}`), bidData('j1')));          // someone else's slot
});
test('bids: homeowner cannot bid on own job', async () => {
  await seed(async (f) => setDoc(doc(f, 'jobs', 'j1'), jobData()));
  await assertFails(setDoc(doc(db(HOME), 'bids', `j1_${HOME}`), bidData('j1', HOME, 'Hana')));
});
test('bids: fake name, bad values rejected', async () => {
  await seed(async (f) => setDoc(doc(f, 'jobs', 'j1'), jobData()));
  const id = `j1_${PRO}`;
  await assertFails(setDoc(doc(db(PRO), 'bids', id), bidData('j1', PRO, 'Someone Else')));
  await assertFails(setDoc(doc(db(PRO), 'bids', id), bidData('j1', PRO, 'Pat', { amount: -1 })));
  await assertFails(setDoc(doc(db(PRO), 'bids', id), bidData('j1', PRO, 'Pat', { amount: 0 })));
  await assertFails(setDoc(doc(db(PRO), 'bids', id), bidData('j1', PRO, 'Pat', { window: 'midnight' })));
});
test('bids: edit own bid while open, not after booking', async () => {
  await seedOpenJobWithBids();
  const edit = { company: 'Pat Carpentry', amount: 240, days: 1, availableDate: '2026-12-03', window: '2–4 PM', message: 'x', createdAt: now() };
  await assertSucceeds(updateDoc(doc(db(PRO), 'bids', `j1_${PRO}`), edit));
  await assertFails(updateDoc(doc(db(PRO2), 'bids', `j1_${PRO}`), edit));
  await assertFails(updateDoc(doc(db(PRO), 'bids', `j1_${PRO}`), { jobId: 'other' }));
  await env.clearFirestore(); await seedUsers(); await seedBookedJob();
  await assertFails(updateDoc(doc(db(PRO), 'bids', `j1_${PRO}`), { ...edit, amount: 9999 }));
});
test('bids: withdraw own bid, but not the booked one', async () => {
  await seedOpenJobWithBids();
  await assertSucceeds(deleteDoc(doc(db(PRO2), 'bids', `j1_${PRO2}`)));
  await env.clearFirestore(); await seedUsers(); await seedBookedJob();
  await assertFails(deleteDoc(doc(db(PRO), 'bids', `j1_${PRO}`)));
  await assertSucceeds(deleteDoc(doc(db(PRO2), 'bids', `j1_${PRO2}`)));
});

// ---------- jobs: lifecycle (B4) ----------
test('jobs: homeowner accepts a real bid', async () => {
  const { b1 } = await seedOpenJobWithBids();
  await assertSucceeds(updateDoc(doc(db(HOME), 'jobs', 'j1'), acceptData(`j1_${PRO}`, b1)));
});
test('jobs: accept must copy the bid exactly', async () => {
  const { b1 } = await seedOpenJobWithBids();
  const ref = doc(db(HOME), 'jobs', 'j1');
  await assertFails(updateDoc(ref, { ...acceptData(`j1_${PRO}`, b1), price: 1 }));            // lowered price
  await assertFails(updateDoc(ref, { ...acceptData(`j1_${PRO}`, b1), contractorId: STRANGER })); // made-up pro
  await assertFails(updateDoc(ref, { ...acceptData('nope', b1) }));                            // no such bid
  await assertFails(updateDoc(doc(db(PRO), 'jobs', 'j1'), acceptData(`j1_${PRO}`, b1)));       // pro can't accept
});
test('jobs: cannot accept a bid from a different job', async () => {
  const { b1 } = await seedOpenJobWithBids();
  await seed(async (f) => setDoc(doc(f, 'jobs', 'j2'), jobData()));
  await assertFails(updateDoc(doc(db(HOME), 'jobs', 'j2'), acceptData(`j1_${PRO}`, b1)));
});
test('jobs: contractor cannot change price, hand off, or reopen', async () => {
  await seedBookedJob();
  const ref = doc(db(PRO), 'jobs', 'j1');
  await assertFails(updateDoc(ref, { price: 99999 }));
  await assertFails(updateDoc(ref, { contractorId: PRO2 }));
  await assertFails(updateDoc(ref, { status: 'open' }));
});
test('jobs: homeowner cannot change price after booking', async () => {
  await seedBookedJob();
  await assertFails(updateDoc(doc(db(HOME), 'jobs', 'j1'), { price: 1 }));
});
test('jobs: either party reschedules; strangers cannot', async () => {
  await seedBookedJob();
  const change = { scheduledDate: '2026-12-10', window: '12–2 PM' };
  await assertSucceeds(updateDoc(doc(db(HOME), 'jobs', 'j1'), change));
  await assertSucceeds(updateDoc(doc(db(PRO), 'jobs', 'j1'), change));
  await assertFails(updateDoc(doc(db(STRANGER), 'jobs', 'j1'), change));
  await assertFails(updateDoc(doc(db(PRO), 'jobs', 'j1'), { scheduledDate: 'soon', window: '12–2 PM' }));
});
test('jobs: only the contractor starts work; either completes; no going back', async () => {
  await seedBookedJob();
  await assertFails(updateDoc(doc(db(HOME), 'jobs', 'j1'), { status: 'in_progress' }));
  await assertSucceeds(updateDoc(doc(db(PRO), 'jobs', 'j1'), { status: 'in_progress' }));
  await assertSucceeds(updateDoc(doc(db(HOME), 'jobs', 'j1'), { status: 'complete', completedAt: now() }));
  await assertFails(updateDoc(doc(db(HOME), 'jobs', 'j1'), { status: 'scheduled' }));
  await assertFails(updateDoc(doc(db(PRO), 'jobs', 'j1'), { scheduledDate: '2026-12-11', window: '12–2 PM' }));
});
test('jobs: delete open job with its bids in one batch; booked jobs cannot be deleted', async () => {
  await seedOpenJobWithBids();
  const f = db(HOME);
  const b = writeBatch(f);
  b.delete(doc(f, 'bids', `j1_${PRO}`));
  b.delete(doc(f, 'bids', `j1_${PRO2}`));
  b.delete(doc(f, 'jobs', 'j1'));
  await assertSucceeds(b.commit());
  await env.clearFirestore(); await seedUsers(); await seedBookedJob();
  await assertFails(deleteDoc(doc(db(HOME), 'jobs', 'j1')));
});

// ---------- work orders ----------
const wo = (over = {}) => ({
  jobId: 'j1', by: PRO, parties: [HOME, PRO], number: 1, lines: [{ desc: 'Labour', qty: 2, rate: 80 }],
  notes: '', approved: false, createdAt: now(), ...over,
});
test('workorders: booked contractor creates, homeowner approves once', async () => {
  await seedBookedJob();
  const ref = await assertSucceeds(addDoc(collection(db(PRO), 'workorders'), wo()));
  await assertFails(updateDoc(doc(db(PRO), 'workorders', ref.id), { approved: true, approvedAt: now() }));
  await assertSucceeds(updateDoc(doc(db(HOME), 'workorders', ref.id), { approved: true, approvedAt: now() }));
  await assertFails(updateDoc(doc(db(HOME), 'workorders', ref.id), { approved: false, approvedAt: now() }));
});
test('workorders: rejected if pre-approved, empty, from others, or on finished jobs', async () => {
  await seedBookedJob();
  await assertFails(addDoc(collection(db(PRO), 'workorders'), wo({ approved: true })));
  await assertFails(addDoc(collection(db(PRO), 'workorders'), wo({ lines: [] })));
  await assertFails(addDoc(collection(db(PRO2), 'workorders'), wo({ by: PRO2 })));
  await env.clearFirestore(); await seedUsers(); await seedBookedJob('complete');
  await assertFails(addDoc(collection(db(PRO), 'workorders'), wo()));
});

// ---------- receipts ----------
const rc = (by, over = {}) => ({
  jobId: 'j1', by, parties: [HOME, PRO], vendor: 'RONA', amount: 42.5, date: '2026-12-02', note: '', createdAt: now(), ...over,
});
test('receipts: either party adds; strangers and bad amounts rejected', async () => {
  await seedBookedJob();
  await assertSucceeds(addDoc(collection(db(HOME), 'receipts'), rc(HOME)));
  await assertSucceeds(addDoc(collection(db(PRO), 'receipts'), rc(PRO)));
  await assertFails(addDoc(collection(db(STRANGER), 'receipts'), rc(STRANGER)));
  await assertFails(addDoc(collection(db(HOME), 'receipts'), rc(HOME, { amount: -10 })));
  await assertFails(addDoc(collection(db(HOME), 'receipts'), rc(PRO)));
});

// ---------- reports ----------
const report = (by, over = {}) => ({
  type: 'job', targetId: 'j1', jobId: 'j1', reason: 'Spam or scam', details: '', by, status: 'new',
  createdAt: now(), ...over,
});
test('reports: anyone signed in can file one; nobody can read or change them', async () => {
  await seedOpenJobWithBids();
  const ref = await assertSucceeds(addDoc(collection(db(PRO), 'reports'), report(PRO)));
  await assertFails(getDoc(doc(db(PRO), 'reports', ref.id)));
  await assertFails(getDoc(doc(db(HOME), 'reports', ref.id)));
  await assertFails(updateDoc(doc(db(PRO), 'reports', ref.id), { status: 'closed' }));
  await assertFails(deleteDoc(doc(db(PRO), 'reports', ref.id)));
  await assertFails(addDoc(collection(anon(), 'reports'), report(PRO)));
  await assertFails(addDoc(collection(db(PRO), 'reports'), report(HOME)));                     // as someone else
  await assertFails(addDoc(collection(db(PRO), 'reports'), report(PRO, { reason: 'I just dislike them' })));
  await assertFails(addDoc(collection(db(PRO), 'reports'), report(PRO, { status: 'closed' })));
});

// ---------- read privacy (H1) and the app's scoped queries (H2) ----------
const q = (f, coll, field, op, val) => getDocs(query(collection(f, coll), where(field, op, val)));

test('jobs: open jobs are listable; nobody can list the whole collection', async () => {
  await seedOpenJobWithBids();
  await assertSucceeds(q(db(STRANGER), 'jobs', 'status', '==', 'open'));
  await assertSucceeds(q(db(HOME), 'jobs', 'postedBy', '==', HOME));
  await assertSucceeds(q(db(PRO), 'jobs', 'contractorId', '==', PRO));
  await assertFails(getDocs(collection(db(STRANGER), 'jobs')));
  await assertFails(q(db(STRANGER), 'jobs', 'postedBy', '==', HOME));        // someone else's job history
  await assertFails(q(anon(), 'jobs', 'status', '==', 'open'));
});
test('jobs: a booked job is visible to its two parties and to bidders only', async () => {
  await seedBookedJob();
  await assertSucceeds(getDoc(doc(db(HOME), 'jobs', 'j1')));
  await assertSucceeds(getDoc(doc(db(PRO), 'jobs', 'j1')));
  await assertSucceeds(getDoc(doc(db(PRO2), 'jobs', 'j1')));                   // bid and lost: sees the outcome
  await assertFails(getDoc(doc(db(STRANGER), 'jobs', 'j1')));
});
test('bids: sealed — only the bidder and the homeowner can read a bid', async () => {
  await seedOpenJobWithBids();
  await assertSucceeds(getDoc(doc(db(PRO), 'bids', `j1_${PRO}`)));
  await assertSucceeds(getDoc(doc(db(HOME), 'bids', `j1_${PRO}`)));
  await assertFails(getDoc(doc(db(PRO2), 'bids', `j1_${PRO}`)));               // competitor
  await assertFails(getDoc(doc(db(STRANGER), 'bids', `j1_${PRO}`)));
  await assertSucceeds(q(db(PRO), 'bids', 'contractorId', '==', PRO));
  await assertSucceeds(q(db(HOME), 'bids', 'jobOwner', '==', HOME));
  await assertFails(q(db(PRO2), 'bids', 'jobId', '==', 'j1'));                 // peeking at a job's bids
  await assertFails(getDocs(collection(db(HOME), 'bids')));
});
test('bids: jobOwner must be the real homeowner', async () => {
  await seed(async (f) => setDoc(doc(f, 'jobs', 'j1'), jobData()));
  await assertFails(setDoc(doc(db(PRO), 'bids', `j1_${PRO}`), bidData('j1', PRO, 'Pat', { jobOwner: PRO })));
  await assertFails(setDoc(doc(db(PRO), 'bids', `j1_${PRO}`), bidData('j1', PRO, 'Pat', { jobOwner: '' })));
  await assertFails(updateDoc(doc(db(PRO), 'bids', `j1_${PRO}`), { jobOwner: PRO }));
});
test('workorders and receipts: only the two parties can read them', async () => {
  await seedBookedJob();
  const w = await assertSucceeds(addDoc(collection(db(PRO), 'workorders'), wo()));
  const r = await assertSucceeds(addDoc(collection(db(HOME), 'receipts'), rc(HOME)));
  for (const uid of [HOME, PRO]) {
    await assertSucceeds(getDoc(doc(db(uid), 'workorders', w.id)));
    await assertSucceeds(getDoc(doc(db(uid), 'receipts', r.id)));
    await assertSucceeds(q(db(uid), 'workorders', 'parties', 'array-contains', uid));
    await assertSucceeds(q(db(uid), 'receipts', 'parties', 'array-contains', uid));
  }
  for (const uid of [PRO2, STRANGER]) {
    await assertFails(getDoc(doc(db(uid), 'workorders', w.id)));
    await assertFails(getDoc(doc(db(uid), 'receipts', r.id)));
  }
  await assertFails(getDocs(collection(db(STRANGER), 'receipts')));
  await assertFails(q(db(STRANGER), 'receipts', 'jobId', '==', 'j1'));
});
test('workorders and receipts: parties must match the job', async () => {
  await seedBookedJob();
  await assertFails(addDoc(collection(db(PRO), 'workorders'), wo({ parties: [PRO, PRO] })));
  await assertFails(addDoc(collection(db(PRO), 'workorders'), wo({ parties: [HOME, PRO, STRANGER] })));
  await assertFails(addDoc(collection(db(HOME), 'receipts'), rc(HOME, { parties: [HOME, STRANGER] })));
  await assertFails(addDoc(collection(db(HOME), 'receipts'), rc(HOME, { parties: [HOME] })));
});

// ---------- sales tax per bid ----------
test('bids: tax rate must be one of the allowed rates', async () => {
  await seed(async (f) => setDoc(doc(f, 'jobs', 'j1'), jobData()));
  const id = `j1_${PRO}`;
  for (const taxRate of [0.07, -0.05, 1, '5%']) {
    await assertFails(setDoc(doc(db(PRO), 'bids', id), bidData('j1', PRO, 'Pat', { taxRate })));
  }
  await assertSucceeds(setDoc(doc(db(PRO), 'bids', id), bidData('j1', PRO, 'Pat', { taxRate: 0.13 })));
  await assertSucceeds(updateDoc(doc(db(PRO), 'bids', id), { taxRate: 0 }));
  await assertSucceeds(updateDoc(doc(db(PRO), 'bids', id), { taxRate: 0.0 }));
});
test('jobs: accepting copies the bid tax rate exactly', async () => {
  const b1 = bidData('j1', PRO, 'Pat', { taxRate: 0.13 });
  await seed(async (f) => {
    await setDoc(doc(f, 'jobs', 'j1'), jobData());
    await setDoc(doc(f, 'bids', `j1_${PRO}`), b1);
  });
  const ref = doc(db(HOME), 'jobs', 'j1');
  await assertFails(updateDoc(ref, acceptData(`j1_${PRO}`, b1)));                        // tax left out
  await assertFails(updateDoc(ref, { ...acceptData(`j1_${PRO}`, b1), taxRate: 0 }));      // tax dropped
  await assertSucceeds(updateDoc(ref, { ...acceptData(`j1_${PRO}`, b1), taxRate: 0.13 }));
});
test('jobs: older bids without a tax rate still accept (GST assumed)', async () => {
  const { b1 } = await seedOpenJobWithBids();
  await assertSucceeds(updateDoc(doc(db(HOME), 'jobs', 'j1'), { ...acceptData(`j1_${PRO}`, b1), taxRate: 0.05 }));
});

// ---------- blocking ----------
const block = (by, other, name = 'X') => setDoc(doc(db(by), 'users', by, 'blocked', other), { name, createdAt: now() });
test('blocking: only you can see or change your block list', async () => {
  await assertSucceeds(block(HOME, PRO, 'Pat'));
  await assertSucceeds(getDoc(doc(db(HOME), 'users', HOME, 'blocked', PRO)));
  await assertSucceeds(getDocs(collection(db(HOME), 'users', HOME, 'blocked')));
  await assertFails(getDoc(doc(db(PRO), 'users', HOME, 'blocked', PRO)));                // the blocked person
  await assertFails(getDocs(collection(db(STRANGER), 'users', HOME, 'blocked')));
  await assertFails(block(PRO, PRO));                                                     // yourself
  await assertFails(setDoc(doc(db(PRO), 'users', HOME, 'blocked', PRO2), { name: 'Q', createdAt: now() }));
  await assertFails(setDoc(doc(db(HOME), 'users', HOME, 'blocked', PRO2), { name: 'Q', createdAt: now(), note: 'x' }));
  await assertFails(deleteDoc(doc(db(PRO), 'users', HOME, 'blocked', PRO)));
  await assertSucceeds(deleteDoc(doc(db(HOME), 'users', HOME, 'blocked', PRO)));
});
test('blocking: a blocked contractor cannot bid on or edit bids for that homeowner', async () => {
  await seedOpenJobWithBids();
  await seed(async (f) => setDoc(doc(f, 'jobs', 'j2'), jobData()));
  await block(HOME, PRO, 'Pat');
  await assertFails(setDoc(doc(db(PRO), 'bids', `j2_${PRO}`), bidData('j2')));
  await assertFails(updateDoc(doc(db(PRO), 'bids', `j1_${PRO}`), { amount: 1 }));
  await assertSucceeds(setDoc(doc(db(PRO2), 'bids', `j2_${PRO2}`), bidData('j2', PRO2, 'Quinn')));  // others unaffected
});
test('blocking: homeowner removes the blocked contractor\'s bids in the same batch', async () => {
  await seedOpenJobWithBids();
  const f = db(HOME);
  const b = writeBatch(f);
  b.set(doc(f, 'users', HOME, 'blocked', PRO), { name: 'Pat', createdAt: now() });
  b.delete(doc(f, 'bids', `j1_${PRO}`));
  await assertSucceeds(b.commit());
});

// ---------- contractor profiles ----------
const profile = (over = {}) => ({
  company: 'Pat Carpentry', about: 'Decks and doors.', trades: ['Carpentry', 'Painting'], serviceArea: 'Victoria',
  years: 12, licence: 'Red Seal carpenter', insured: true, updatedAt: now(), ...over,
});
test('profiles: owner writes a valid profile; everyone signed in can read it', async () => {
  await assertSucceeds(setDoc(doc(db(PRO), 'profiles', PRO), profile()));
  await assertSucceeds(getDoc(doc(db(HOME), 'profiles', PRO)));
  await assertSucceeds(getDoc(doc(db(STRANGER), 'profiles', PRO)));
  await assertFails(getDoc(doc(anon(), 'profiles', PRO)));
  await assertFails(setDoc(doc(db(HOME), 'profiles', PRO), profile()));                    // someone else's
  await assertFails(deleteDoc(doc(db(HOME), 'profiles', PRO)));
  await assertSucceeds(deleteDoc(doc(db(PRO), 'profiles', PRO)));
});
test('profiles: field validation', async () => {
  const bad = [
    { trades: ['Hacking'] }, { trades: 'Carpentry' }, { years: 81 }, { years: -1 }, { years: 2.5 },
    { insured: 'yes' }, { about: 'x'.repeat(501) }, { verified: true },
  ];
  for (const over of bad) await assertFails(setDoc(doc(db(PRO), 'profiles', PRO), profile(over)));
  await assertSucceeds(setDoc(doc(db(PRO), 'profiles', PRO), profile({ trades: [], licence: '', about: '', years: 0, insured: false })));
});

// ---------- messages ----------
const msg = (by, over = {}) => ({ jobId: 'j1', parties: [HOME, PRO], by, text: 'Hi, the gate code is 1234', createdAt: now(), ...over });
test('messages: the two parties on a booked job can message and read', async () => {
  await seedBookedJob();
  const m1 = await assertSucceeds(addDoc(collection(db(HOME), 'messages'), msg(HOME)));
  await assertSucceeds(addDoc(collection(db(PRO), 'messages'), msg(PRO)));
  await assertSucceeds(getDoc(doc(db(PRO), 'messages', m1.id)));
  await assertSucceeds(getDocs(query(collection(db(HOME), 'messages'), where('parties', 'array-contains', HOME))));
  await assertFails(getDoc(doc(db(PRO2), 'messages', m1.id)));                              // losing bidder
  await assertFails(getDocs(collection(db(STRANGER), 'messages')));
  await assertFails(updateDoc(doc(db(HOME), 'messages', m1.id), { text: 'edited' }));
  await assertFails(deleteDoc(doc(db(HOME), 'messages', m1.id)));
});
test('messages: rejected on open jobs, from outsiders, as someone else, or empty', async () => {
  await seedOpenJobWithBids();
  await assertFails(addDoc(collection(db(HOME), 'messages'), msg(HOME, { parties: [HOME, PRO] })));   // not booked yet
  await env.clearFirestore(); await seedUsers(); await seedBookedJob();
  await assertFails(addDoc(collection(db(PRO2), 'messages'), msg(PRO2, { parties: [HOME, PRO2] })));
  await assertFails(addDoc(collection(db(STRANGER), 'messages'), msg(STRANGER)));
  await assertFails(addDoc(collection(db(HOME), 'messages'), msg(PRO)));                     // pretending to be the pro
  await assertFails(addDoc(collection(db(HOME), 'messages'), msg(HOME, { text: '' })));
  await assertFails(addDoc(collection(db(HOME), 'messages'), msg(HOME, { text: 'x'.repeat(1001) })));
  await assertFails(addDoc(collection(db(HOME), 'messages'), msg(HOME, { parties: [HOME, PRO, STRANGER] })));
});
test('messages: still allowed after the job is complete', async () => {
  await seedBookedJob('complete');
  await assertSucceeds(addDoc(collection(db(HOME), 'messages'), msg(HOME, { text: 'Thanks, great work' })));
});
