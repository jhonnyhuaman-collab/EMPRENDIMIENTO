import { zipFunctions } from '@netlify/zip-it-and-ship-it';
import { mkdir } from 'node:fs/promises';
await mkdir('.local/functions', { recursive: true });
const result = await zipFunctions('netlify/functions', '.local/functions', { config: { '*': { nodeBundler: 'esbuild' } } });
if (result.length !== 1 || result[0].name !== 'api') throw new Error('No se encontro la funcion api.');
console.log('Funcion api empaquetada para Netlify.');
