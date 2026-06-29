export interface SemanticMotionTarget {
  group: string
  index?: number
}

export const semanticExpressions: Record<string, string> = {
  happy: 'exp_01',
  sad: 'exp_02',
  surprised: 'exp_03',
  thinking: 'exp_04',
  error: 'exp_05',
  annoyed: 'exp_05',   //  annoyed → reuse error/frown expression
  neutral: 'exp_01',
}

export const semanticMotions: Record<string, SemanticMotionTarget> = {
  idle: { group: '', index: 2 },
  greet: { group: '', index: 1 },
  wave: { group: '', index: 5 },
  nod: { group: '', index: 6 },
  shake_head: { group: '', index: 9 },
  touch_body: { group: '', index: 10 },
  touch_head: { group: '', index: 11 },
  touch_special: { group: '', index: 12 },
  home: { group: '', index: 1 },
  mail: { group: '', index: 4 },
  main_1: { group: '', index: 5 },
  main_2: { group: '', index: 6 },
  main_3: { group: '', index: 7 },
  thinking: { group: '', index: 2 },
  speaking: { group: '', index: 5 },
}
