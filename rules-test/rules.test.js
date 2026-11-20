// Tests for ../firestore.rules against the Firestore emulator.
// Run from this folder:  npm install  then  npm test
// The writes below mirror exactly what the Android app sends (ui/AppViewModel.kt).

import { test, before, beforeEach, after } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  initializeTestEnvironment, assertSucceeds, assertFails,
} from '@firebase/rules-unit-testing';
import {
  doc, setDoc, addDoc, updateDoc, deleteDoc, getDoc, collection, writeBatch,
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
  message: 'Can do', createdAt: now(), jobId, contractorId: uid, contractorName: name, ...over,
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
  jobId: 'j1', by: PRO, number: 1, lines: [{ desc: 'Labour', qty: 2, rate: 80 }],
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
  jobId: 'j1', by, vendor: 'RONA', amount: 42.5, date: '2026-12-02', note: '', createdAt: now(), ...over,
});
test('receipts: either party adds; strangers and bad amounts rejected', async () => {
  await seedBookedJob();
  await assertSucceeds(addDoc(collection(db(HOME), 'receipts'), rc(HOME)));
  await assertSucceeds(addDoc(collection(db(PRO), 'receipts'), rc(PRO)));
  await assertFails(addDoc(collection(db(STRANGER), 'receipts'), rc(STRANGER)));
  await assertFails(addDoc(collection(db(HOME), 'receipts'), rc(HOME, { amount: -10 })));
  await assertFails(addDoc(collection(db(HOME), 'receipts'), rc(PRO)));
});
