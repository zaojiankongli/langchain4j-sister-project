#!/usr/bin/env node
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const fixturesDir = join(__dirname, 'fixtures');
const schemasDir = join(__dirname, 'schemas');

const messageSchemas = new Map([
  ['user.message', 'user-message.schema.json'],
  ['assistant.message.delta', 'assistant-message-delta.schema.json'],
  ['assistant.message.done', 'assistant-message-done.schema.json'],
  ['pet.expression', 'pet-expression.schema.json'],
  ['pet.motion', 'pet-motion.schema.json'],
  ['tool.request', 'tool-request.schema.json'],
  ['tool.confirmed', 'tool-confirmed.schema.json'],
  ['tool.rejected', 'tool-rejected.schema.json'],
  ['error', 'error.schema.json'],
]);

const semanticExpressions = new Set(['neutral', 'happy', 'sad', 'surprised', 'thinking', 'error']);
const semanticMotions = new Set(['idle', 'greet', 'wave', 'nod', 'shake_head', 'thinking', 'speaking']);
const motionPriorities = new Set(['low', 'normal', 'high']);
const toolRejectReasons = new Set(['user_rejected', 'permission_denied', 'timeout']);
const errorCodes = new Set(['INVALID_MESSAGE', 'UNKNOWN_MESSAGE_TYPE', 'UNSUPPORTED_VERSION', 'TOOL_NOT_ALLOWED', 'INTERNAL_ERROR']);

function error(code, message, field = undefined) {
  return { ok: false, code, message, ...(field ? { field } : {}) };
}

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function requireString(message, field, { nonEmpty = true } = {}) {
  if (typeof message[field] !== 'string') return error('INVALID_MESSAGE', `${field} must be a string`, field);
  if (nonEmpty && message[field].length === 0) return error('INVALID_MESSAGE', `${field} must not be empty`, field);
  return null;
}

function requireNumberRange(message, field, min, max) {
  if (!(field in message)) return null;
  if (typeof message[field] !== 'number' || Number.isNaN(message[field])) return error('INVALID_MESSAGE', `${field} must be a number`, field);
  if (message[field] < min || message[field] > max) return error('INVALID_MESSAGE', `${field} must be between ${min} and ${max}`, field);
  return null;
}

function forbidAdditional(message, allowed) {
  for (const key of Object.keys(message)) {
    if (!allowed.has(key)) return error('INVALID_MESSAGE', `${key} is not allowed for ${message.type}`, key);
  }
  return null;
}

function validateEnvelope(message) {
  if (!isObject(message)) return error('INVALID_MESSAGE', 'message must be a JSON object');
  const versionError = requireString(message, 'version');
  if (versionError) return versionError;
  if (message.version !== '1') return error('UNSUPPORTED_VERSION', 'version must be "1"', 'version');
  const typeError = requireString(message, 'type');
  if (typeError) return typeError;
  if (!messageSchemas.has(message.type)) return error('UNKNOWN_MESSAGE_TYPE', `unknown message type: ${message.type}`, 'type');
  return null;
}

function validateMessage(message) {
  const envelopeError = validateEnvelope(message);
  if (envelopeError) return envelopeError;

  switch (message.type) {
    case 'user.message':
    case 'assistant.message.delta': {
      const additional = forbidAdditional(message, new Set(['version', 'type', 'conversationId', 'messageId', 'text']));
      return additional || requireString(message, 'conversationId') || requireString(message, 'messageId') || requireString(message, 'text') || { ok: true };
    }
    case 'assistant.message.done': {
      const additional = forbidAdditional(message, new Set(['version', 'type', 'conversationId', 'messageId']));
      return additional || requireString(message, 'conversationId') || requireString(message, 'messageId') || { ok: true };
    }
    case 'pet.expression': {
      const additional = forbidAdditional(message, new Set(['version', 'type', 'expression', 'intensity', 'durationMs']));
      if (additional) return additional;
      const expressionError = requireString(message, 'expression');
      if (expressionError) return expressionError;
      if (!semanticExpressions.has(message.expression)) return error('INVALID_MESSAGE', 'expression must be a semantic expression name', 'expression');
      const intensityError = requireNumberRange(message, 'intensity', 0, 1);
      if (intensityError) return intensityError;
      if ('durationMs' in message && (!Number.isInteger(message.durationMs) || message.durationMs < 1 || message.durationMs > 60000)) {
        return error('INVALID_MESSAGE', 'durationMs must be an integer between 1 and 60000', 'durationMs');
      }
      return { ok: true };
    }
    case 'pet.motion': {
      const additional = forbidAdditional(message, new Set(['version', 'type', 'motion', 'priority']));
      if (additional) return additional;
      const motionError = requireString(message, 'motion');
      if (motionError) return motionError;
      if (!semanticMotions.has(message.motion)) return error('INVALID_MESSAGE', 'motion must be a semantic motion name', 'motion');
      if ('priority' in message && !motionPriorities.has(message.priority)) return error('INVALID_MESSAGE', 'priority must be low, normal, or high', 'priority');
      return { ok: true };
    }
    case 'tool.request': {
      const additional = forbidAdditional(message, new Set(['version', 'type', 'toolCallId', 'tool', 'args', 'requiresConfirmation', 'message']));
      if (additional) return additional;
      const base = requireString(message, 'toolCallId') || requireString(message, 'tool') || requireString(message, 'message');
      if (base) return base;
      if (message.tool !== 'open_url') return error('TOOL_NOT_ALLOWED', 'only open_url is allowed in MVP', 'tool');
      if (!isObject(message.args)) return error('INVALID_MESSAGE', 'args must be an object', 'args');
      if (message.requiresConfirmation !== true) return error('INVALID_MESSAGE', 'requiresConfirmation must be true for MVP tool requests', 'requiresConfirmation');
      return { ok: true };
    }
    case 'tool.confirmed': {
      const additional = forbidAdditional(message, new Set(['version', 'type', 'toolCallId']));
      return additional || requireString(message, 'toolCallId') || { ok: true };
    }
    case 'tool.rejected': {
      const additional = forbidAdditional(message, new Set(['version', 'type', 'toolCallId', 'reason']));
      if (additional) return additional;
      const base = requireString(message, 'toolCallId') || requireString(message, 'reason');
      if (base) return base;
      if (!toolRejectReasons.has(message.reason)) return error('INVALID_MESSAGE', 'reason is not an allowed rejection reason', 'reason');
      return { ok: true };
    }
    case 'error': {
      const additional = forbidAdditional(message, new Set(['version', 'type', 'code', 'message', 'correlationId', 'details']));
      if (additional) return additional;
      const base = requireString(message, 'code') || requireString(message, 'message');
      if (base) return base;
      if (!errorCodes.has(message.code)) return error('INVALID_MESSAGE', 'code is not an allowed error code', 'code');
      if ('correlationId' in message && typeof message.correlationId !== 'string') return error('INVALID_MESSAGE', 'correlationId must be a string', 'correlationId');
      if ('details' in message && !isObject(message.details)) return error('INVALID_MESSAGE', 'details must be an object', 'details');
      return { ok: true };
    }
    default:
      return error('UNKNOWN_MESSAGE_TYPE', `unknown message type: ${message.type}`, 'type');
  }
}

function loadJson(path) {
  return JSON.parse(readFileSync(path, 'utf8'));
}

function validateFile(path) {
  const message = loadJson(path);
  const result = validateMessage(message);
  const schema = messageSchemas.get(message?.type);
  return { path, schema: schema ? join(schemasDir, schema) : join(schemasDir, 'pet-ws-v1-message.schema.json'), result };
}

function printResult(item) {
  const relativePath = item.path.startsWith(process.cwd()) ? item.path.slice(process.cwd().length + 1) : item.path;
  if (item.result.ok) {
    console.log(`PASS ${relativePath}`);
    return;
  }
  console.log(`FAIL ${relativePath} ${JSON.stringify({ code: item.result.code, message: item.result.message, field: item.result.field })}`);
}

const args = process.argv.slice(2);
const files = args.length > 0
  ? args.map((arg) => resolve(process.cwd(), arg))
  : readdirSync(fixturesDir).filter((name) => name.endsWith('.json')).map((name) => join(fixturesDir, name));

let failures = 0;
for (const file of files) {
  try {
    const item = validateFile(file);
    const basename = file.split(/[\\/]/).pop();
    const expectedInvalid = basename.startsWith('invalid-');
    const passed = item.result.ok;
    if (expectedInvalid ? passed : !passed) failures += 1;
    printResult(item);
    if (expectedInvalid && !passed) {
      console.log(`EXPECTED_INVALID ${basename} ${item.result.code}`);
    }
  } catch (err) {
    failures += 1;
    console.log(`FAIL ${file} ${JSON.stringify({ code: 'VALIDATOR_ERROR', message: err.message })}`);
  }
}

if (failures > 0) process.exit(1);
