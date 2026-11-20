import { readFileSync } from 'node:fs';

/** The Firebase project ID from app/google-services.json, so the emulators match what the app expects. */
export function projectId() {
  try {
    const json = JSON.parse(readFileSync(new URL('../app/google-services.json', import.meta.url), 'utf8'));
    return json.project_info.project_id;
  } catch {
    return 'demo-workbid';
  }
}
