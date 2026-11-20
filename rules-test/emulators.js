// Starts the local Firebase emulators (Firestore + Auth) under this app's project ID,
// so a debug build made with -Pworkbid.emulator=true can use them. Nothing here touches live data.
// Usage (from this folder):  npm run emulators      then, in a second window:  npm run seed
import { spawn } from 'node:child_process';
import { projectId } from './project.js';

const child = spawn(
  'npx',
  ['firebase', 'emulators:start', '--only', 'firestore,auth', '--project', projectId(), '--config', '../firebase.json'],
  { stdio: 'inherit', shell: true },
);
child.on('exit', (code) => process.exit(code ?? 0));
