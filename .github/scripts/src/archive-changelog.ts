import { writeFileSync } from 'node:fs'
import {
  LINK_PATTERN,
  UNRELEASED,
  fail,
  findHeading,
  readLines,
  readSection,
  requireEnv,
} from './changelog.ts'

function main(): void {
  const version = requireEnv('RELEASE_VERSION')
  const changelogPath = requireEnv('CHANGELOG_PATH')
  const repoUrl = requireEnv('REPO_URL')
  const lines = readLines(changelogPath)

  // 已有该版本的标题（手动写好或之前已归档过）时不再改动
  if (findHeading(lines, version) !== -1) {
    console.log(`CHANGELOG.md 中已有 ${version}，无需归档`)
    return
  }

  const unreleasedIndex = findHeading(lines, UNRELEASED)
  if (unreleasedIndex === -1) {
    fail(`CHANGELOG.md 中没有 ## [${UNRELEASED}] 标题`)
  }
  if (!readSection(lines, unreleasedIndex)) {
    fail(`CHANGELOG.md 中没有 ${version} 的更新日志，请先写在 ${UNRELEASED} 下！`)
  }

  // 把 Unreleased 下的内容归到新版本，上方留一个空的 Unreleased，日期按北京时间
  const date = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Shanghai' })
  lines.splice(unreleasedIndex + 1, 0, '', `## [${version}] - ${date}`)

  // 新版本的链接放在已有链接的最前面，没有链接时追加到文件末尾
  const link = `[${version}]: ${repoUrl}/releases/tag/v${version}`
  const linkIndex = lines.findIndex((line) => LINK_PATTERN.test(line))
  if (linkIndex === -1) {
    while (lines.at(-1) === '') {
      lines.pop()
    }
    lines.push('', link, '')
  } else {
    lines.splice(linkIndex, 0, link)
  }

  writeFileSync(changelogPath, lines.join('\n'))
  console.log(`已将 ${UNRELEASED} 的更新日志归档为 ${version}`)
}

main()
