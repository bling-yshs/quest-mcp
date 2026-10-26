import { readFileSync } from 'node:fs'
import process from 'node:process'

export const UNRELEASED = 'Unreleased'
export const LINK_PATTERN = /^\[[^\]]+\]: /

export function fail(message: string): never {
  console.error(message)
  process.exit(1)
}

export function requireEnv(name: string): string {
  const value = process.env[name]
  if (!value) {
    fail(`缺少环境变量 ${name}`)
  }
  return value
}

export function readLines(path: string): string[] {
  return readFileSync(path, 'utf8').split(/\r?\n/)
}

// 版本标题格式：## [版本号] 或 ## [版本号] - 日期
export function findHeading(lines: string[], name: string): number {
  return lines.findIndex((line) => line === `## [${name}]` || line.startsWith(`## [${name}] `))
}

// 取标题下的内容，直到下一个版本标题或底部的链接定义
export function readSection(lines: string[], headingIndex: number): string {
  const rest = lines.slice(headingIndex + 1)
  const end = rest.findIndex((line) => line.startsWith('## [') || LINK_PATTERN.test(line))
  return rest
    .slice(0, end === -1 ? undefined : end)
    .join('\n')
    .trim()
}
