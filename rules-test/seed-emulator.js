// Fills the LOCAL emulators with realistic demo accounts and jobs, for testing and screenshots.
// It refuses to run unless the emulators are running, and never touches live Firebase data.
//
// Demo sign-ins (emulator only):
//   Homeowner   hana@example.com   workbid-demo
//   Contractor  pat@example.com    workbid-demo
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, setDoc } from 'firebase/firestore';
import { projectId } from './project.js';

const PID = projectId();
const AUTH = 'http://127.0.0.1:9099';
const PASSWORD = 'workbid-demo';

const people = [
  { uid: 'demo-hana', name: 'Hana Lee', email: 'hana@example.com' },
  { uid: 'demo-pat', name: 'Pat Morgan', email: 'pat@example.com' },
  { uid: 'demo-quinn', name: 'Quinn Alvarez', email: 'quinn@example.com' },
  { uid: 'demo-sam', name: 'Sam Okafor', email: 'sam@example.com' },
  { uid: 'demo-riya', name: 'Riya Shah', email: 'riya@example.com' },
  { uid: 'demo-tom', name: 'Tom Becker', email: 'tom@example.com' },
];

const day = 86400000;
const now = Date.now();
const iso = (offsetDays) => new Date(now + offsetDays * day).toISOString().slice(0, 10);

async function resetAuth() {
  await fetch(`${AUTH}/emulator/v1/projects/${PID}/accounts`, { method: 'DELETE' });
}

async function createUser(p) {
  const res = await fetch(`${AUTH}/identitytoolkit.googleapis.com/v1/projects/${PID}/accounts`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer owner' },
    body: JSON.stringify({ localId: p.uid, email: p.email, password: PASSWORD, displayName: p.name, emailVerified: true }),
  });
  if (!res.ok) throw new Error(`Could not create ${p.email}: ${res.status} ${await res.text()}`);
}

const job = (over) => ({
  ref: 'WB-' + Math.floor(10000 + Math.random() * 89999), budgetMin: 0, budgetMax: 0, timing: 'Flexible',
  preferDate: iso(7), status: 'open', acceptedBidId: '', contractorId: '', ...over,
});
const bid = (jobId, uid, name, company, amount, days, offset, window, message, ageHours) => ({
  id: `${jobId}_${uid}`, jobId, contractorId: uid, contractorName: name, company, amount, days,
  availableDate: iso(offset), window, message, createdAt: now - ageHours * 3600000,
});

async function main() {
  try {
    await fetch(`${AUTH}/`);
  } catch {
    console.error('The Auth emulator is not running. Start it first with:  npm run emulators');
    process.exit(1);
  }

  await resetAuth();
  for (const p of people) await createUser(p);

  const env = await initializeTestEnvironment({ projectId: PID, firestore: { host: '127.0.0.1', port: 8085 } });
  await env.clearFirestore();

  const jobs = {
    faucet: job({
      title: 'Replace kitchen faucet and leaky shut-off valve', category: 'Plumbing', area: 'Fairfield, Victoria',
      description: 'Single-handle faucet over a double sink. The hot shut-off under the sink drips. I have the new faucet already (Moen, pull-down). Easy access, cabinet is empty.',
      budgetMin: 200, budgetMax: 400, timing: 'This week', preferDate: iso(3),
      postedBy: 'demo-hana', postedByName: 'Hana Lee', createdAt: now - 26 * 3600000,
    }),
    paint: job({
      title: 'Paint two bedrooms (walls only)', category: 'Painting', area: 'Fairfield, Victoria',
      description: 'Two bedrooms, about 11x12 and 10x10, 8 ft ceilings. Walls only, same colour, minor patching. Furniture will be moved to the middle.',
      budgetMin: 900, budgetMax: 1400, timing: 'Within 2 weeks',
      postedBy: 'demo-hana', postedByName: 'Hana Lee', createdAt: now - 6 * day,
      status: 'scheduled', contractorId: 'demo-pat', contractorName: 'Pat Morgan', company: 'Harbour Trades Co.',
      price: 1150, scheduledDate: iso(2), window: '8–10 AM',
    }),
    fan: job({
      title: 'Install bathroom exhaust fan with timer switch', category: 'Electrical', area: 'Fairfield, Victoria',
      description: 'Main bathroom has no fan. Attic access above. Need fan, ducting to the soffit vent, and a timer switch by the door.',
      budgetMin: 400, budgetMax: 700, timing: 'Flexible',
      postedBy: 'demo-hana', postedByName: 'Hana Lee', createdAt: now - 12 * day,
      status: 'in_progress', contractorId: 'demo-pat', contractorName: 'Pat Morgan', company: 'Harbour Trades Co.',
      price: 560, scheduledDate: iso(0), window: '10 AM–12 PM',
    }),
    fence: job({
      title: 'Repair two leaning fence panels', category: 'Carpentry', area: 'Fairfield, Victoria',
      description: 'Two cedar panels leaning after the windstorm, one post is loose.',
      budgetMin: 150, budgetMax: 300, postedBy: 'demo-hana', postedByName: 'Hana Lee', createdAt: now - 30 * day,
      status: 'complete', contractorId: 'demo-quinn', contractorName: 'Quinn Alvarez', company: 'Alvarez Carpentry',
      price: 240, scheduledDate: iso(-20), window: '12–2 PM', completedAt: now - 20 * day,
    }),
    steps: job({
      title: 'Rebuild crumbling garden steps', category: 'Landscaping', area: 'Oak Bay',
      description: 'Five concrete steps down to the back garden are cracking and uneven. Open to pavers or timber. About 4 ft wide.',
      budgetMin: 1500, budgetMax: 3000, timing: 'Within 2 weeks',
      postedBy: 'demo-riya', postedByName: 'Riya Shah', createdAt: now - 5 * 3600000,
    }),
    door: job({
      title: 'Fix sticking patio door', category: 'Carpentry', area: 'James Bay, Victoria',
      description: 'Sliding patio door drags and is hard to lock. Probably rollers or track. Door is about 15 years old.',
      budgetMin: 100, budgetMax: 250, timing: 'This week',
      postedBy: 'demo-tom', postedByName: 'Tom Becker', createdAt: now - 2 * 3600000,
    }),
    furnace: job({
      title: 'Gas furnace not igniting', category: 'HVAC', area: 'Saanich',
      description: 'Furnace clicks but will not light. About 9 years old, last serviced two years ago. Need someone soon, it is getting cold.',
      timing: 'Urgent (24–48 h)', preferDate: iso(1),
      postedBy: 'demo-sam', postedByName: 'Sam Okafor', createdAt: now - 40 * 60000,
    }),
    outlets: job({
      title: 'Replace 6 kitchen outlets with GFCI', category: 'Electrical', area: 'Esquimalt',
      description: 'Older kitchen, outlets are not GFCI. Want all six counter outlets updated. Panel is in the basement.',
      budgetMin: 300, budgetMax: 600, timing: 'Flexible',
      postedBy: 'demo-riya', postedByName: 'Riya Shah', createdAt: now - 3 * day,
    }),
  };

  const bids = [
    bid('faucet', 'demo-pat', 'Pat Morgan', 'Harbour Trades Co.', 285, 1, 3, '10 AM–12 PM',
      'Includes new quarter-turn shut-off valves for hot and cold. Licensed and insured, 1-year warranty on labour.', 20),
    bid('faucet', 'demo-quinn', 'Quinn Alvarez', 'Alvarez Carpentry', 240, 1, 5, '2–4 PM',
      'Can swap the faucet and the hot shut-off. Cold valve extra if needed.', 14),
    bid('faucet', 'demo-sam', 'Sam Okafor', 'Okafor Plumbing & Heating', 330, 1, 2, '8–10 AM',
      'Red Seal plumber. Price covers both valves, supply lines and haul-away of the old faucet.', 6),
    bid('paint', 'demo-pat', 'Pat Morgan', 'Harbour Trades Co.', 1150, 2, 2, '8–10 AM', 'Two coats, premium paint included.', 140),
    bid('fan', 'demo-pat', 'Pat Morgan', 'Harbour Trades Co.', 560, 1, 0, '10 AM–12 PM', 'Panasonic WhisperFit, includes ducting.', 280),
    bid('fence', 'demo-quinn', 'Quinn Alvarez', 'Alvarez Carpentry', 240, 1, -20, '12–2 PM', 'Will reset the post in concrete.', 700),
    bid('outlets', 'demo-pat', 'Pat Morgan', 'Harbour Trades Co.', 480, 1, 6, '12–2 PM', 'Permit included.', 30),
    bid('steps', 'demo-quinn', 'Quinn Alvarez', 'Alvarez Carpentry', 2400, 3, 9, '8–10 AM', 'Timber steps with gravel landings.', 2),
  ];
  // Point booked jobs at their accepted bids.
  jobs.paint.acceptedBidId = 'paint_demo-pat';
  jobs.fan.acceptedBidId = 'fan_demo-pat';
  jobs.fence.acceptedBidId = 'fence_demo-quinn';

  await env.withSecurityRulesDisabled(async (ctx) => {
    const f = ctx.firestore();
    for (const p of people) await setDoc(doc(f, 'users', p.uid), { name: p.name, createdAt: now - 60 * day });
    for (const [id, data] of Object.entries(jobs)) await setDoc(doc(f, 'jobs', id), data);
    for (const { id, ...data } of bids) await setDoc(doc(f, 'bids', id), data);
    await setDoc(doc(f, 'workorders', 'wo-fan-1'), {
      jobId: 'fan', by: 'demo-pat', number: 1, approved: false, createdAt: now - 2 * 3600000,
      notes: 'Includes patching the ceiling around the new fan. Paint touch-up not included.',
      lines: [
        { desc: 'Panasonic WhisperFit fan', qty: 1, rate: 189 },
        { desc: 'Insulated duct + soffit vent kit', qty: 1, rate: 64 },
        { desc: 'Labour', qty: 3.5, rate: 85 },
      ],
    });
    await setDoc(doc(f, 'receipts', 'rc-fan-1'), {
      jobId: 'fan', by: 'demo-pat', vendor: 'Home Depot Victoria', amount: 271.6, date: iso(0),
      note: 'Fan, duct kit, timer switch', createdAt: now - 3 * 3600000,
    });
  });
  await env.cleanup();
  console.log(`Seeded ${people.length} accounts, ${Object.keys(jobs).length} jobs and ${bids.length} bids in the local emulators.`);
  console.log(`Sign in on the emulator build as hana@example.com or pat@example.com, password ${PASSWORD}`);
}

main().catch((e) => { console.error(e); process.exit(1); });
