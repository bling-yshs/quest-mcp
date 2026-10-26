import { writeFileSync } from 'node:fs'
import { UNRELEASED, fail, findHeading, readLines, readSection, requireEnv } from './changelog.ts'

function main(): void {
  const lines = readLines(requireEnv('CHANGELOG_PATH'))
  const unreleasedIndex = findHeading(lines, UNRELEASED)
  if (unreleasedIndex === -1) {
    fail(`CHANGELOG.md 中没有 ## [${UNRELEASED}] 标题`)
  }
  // 内容为空时写入空文件，由调用方决定是否跳过
  const body = readSection(lines, unreleasedIndex)
  writeFileSync(requireEnv('RELEASE_BODY_PATH'), body ? `${body}\n` : '')
}

main()
