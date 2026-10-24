import { createHash } from 'node:crypto'
import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import process from 'node:process'

const MODRINTH_API = 'https://api.modrinth.com/v2'
const USER_AGENT = 'bling-yshs/quest-mcp'
const MOD_ID = 'questmcp'
// JEI 在 Modrinth 上的项目 ID
const JEI_PROJECT_ID = 'u6dRKJwZ'

const LOADER_DISPLAY_NAMES: Record<string, string> = {
  forge: 'Forge',
  neoforge: 'NeoForge',
}

interface ModJar {
  path: string
  fileName: string
  loader: string
  mcVersion: string
  modVersion: string
}

function fail(message: string): never {
  console.error(message)
  process.exit(1)
}

function requireEnv(name: string): string {
  const value = process.env[name]
  if (!value) {
    fail(`缺少环境变量 ${name}`)
  }
  return value
}

// 文件名格式：<mod_id>-<loader>-<mc 版本>-<mod 版本>.jar，mc 版本可能包含 -，所以取中间所有段
function parseJar(dir: string, fileName: string): ModJar | null {
  if (!fileName.endsWith('.jar')) {
    return null
  }
  const parts = fileName.slice(0, -'.jar'.length).split('-')
  if (parts.length < 4 || parts[0] !== MOD_ID) {
    return null
  }
  return {
    path: join(dir, fileName),
    fileName,
    loader: parts[1],
    mcVersion: parts.slice(2, -1).join('-'),
    modVersion: parts[parts.length - 1],
  }
}

async function isUploaded(sha1: string): Promise<boolean> {
  const response = await fetch(`${MODRINTH_API}/version_file/${sha1}?algorithm=sha1`, {
    headers: { 'User-Agent': USER_AGENT },
  })
  if (response.status === 404) {
    return false
  }
  if (!response.ok) {
    fail(`查询文件 ${sha1} 失败：${response.status} ${await response.text()}`)
  }
  return true
}

async function upload(
  jar: ModJar,
  content: Uint8Array<ArrayBuffer>,
  options: {
    token: string
    projectId: string
    changelog: string
  },
): Promise<void> {
  const loaderName = LOADER_DISPLAY_NAMES[jar.loader] ?? jar.loader
  const data = {
    project_id: options.projectId,
    name: `[${loaderName} ${jar.mcVersion}] v${jar.modVersion}`,
    version_number: jar.modVersion,
    changelog: options.changelog,
    dependencies: [{ project_id: JEI_PROJECT_ID, dependency_type: 'required' }],
    game_versions: [jar.mcVersion],
    version_type: 'release',
    loaders: [jar.loader],
    featured: true,
    file_parts: ['file'],
    primary_file: 'file',
  }

  const form = new FormData()
  form.append('data', JSON.stringify(data))
  form.append('file', new Blob([content], { type: 'application/java-archive' }), jar.fileName)

  const response = await fetch(`${MODRINTH_API}/version`, {
    method: 'POST',
    headers: { Authorization: options.token, 'User-Agent': USER_AGENT },
    body: form,
  })
  if (!response.ok) {
    fail(`上传 ${jar.fileName} 失败：${response.status} ${await response.text()}`)
  }
  console.log(`已上传 ${jar.fileName}：${data.name}`)
}

async function main(): Promise<void> {
  const token = requireEnv('MODRINTH_TOKEN')
  const projectId = requireEnv('MODRINTH_PROJECT_ID')
  const version = requireEnv('RELEASE_VERSION')
  const assetsDir = requireEnv('RELEASE_ASSETS_DIR')
  const changelog = readFileSync(requireEnv('RELEASE_BODY_PATH'), 'utf8')
  const selectedVersions = requireEnv('SELECTED_MC_VERSIONS')
    .split(',')
    .map((mcVersion) => mcVersion.trim())
    .filter(Boolean)

  const allJars = readdirSync(assetsDir)
    .map((fileName) => parseJar(assetsDir, fileName))
    .filter((jar) => jar !== null)
  const jars = allJars.filter((jar) => selectedVersions.includes(jar.mcVersion))

  // 所有选中的版本都必须有对应的 Release 文件，避免只发布部分版本
  const missingVersions = selectedVersions.filter(
    (mcVersion) => !jars.some((jar) => jar.mcVersion === mcVersion),
  )
  if (missingVersions.length > 0) {
    fail(`Release 中缺少这些版本的文件：${missingVersions.join(',')}`)
  }
  const mismatchedJars = jars.filter((jar) => jar.modVersion !== version)
  if (mismatchedJars.length > 0) {
    fail(
      `这些文件的 mod 版本不等于 ${version}：${mismatchedJars.map((jar) => jar.fileName).join(',')}`,
    )
  }

  for (const jar of jars) {
    const content = new Uint8Array(readFileSync(jar.path))
    const sha1 = createHash('sha1').update(content).digest('hex')
    // 重跑时跳过已上传的文件，Modrinth 不允许重复上传同一文件
    if (await isUploaded(sha1)) {
      console.log(`已存在，跳过 ${jar.fileName}`)
      continue
    }
    await upload(jar, content, { token, projectId, changelog })
  }
}

await main()
