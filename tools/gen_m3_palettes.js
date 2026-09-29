/**
 * 重新生成 Material 3 官方 HCT 色调板表 → M3Palettes.kt
 *
 * 用法:
 *   1. 在任意目录 npm i @material/material-color-utilities@0.2.0 esbuild
 *   2. 复制本文件到该目录,改名 gen_palettes.js
 *   3. npx esbuild gen_palettes.js --bundle --platform=node --format=cjs --outfile=gen.cjs
 *   4. node gen.cjs
 *
 * 原理:对 0°~350° 每 10° 取一个标准鲜艳种子色(HCT 36,40),
 * 用官方 themeFromSourceColor(TonalSpot 方案)推导 36 个色彩角色,
 * 再用中性色板补 surfaceContainer* 家族,得到 36 行 × 72 列(浅/深)的表。
 * 表存 24 位 RGB(去掉不透明的 FF 字节),查询时补回。
 */
const { Hct, themeFromSourceColor } = require('@material/material-color-utilities');
const fs = require('fs');
const path = require('path');

const ROLES = [
  'primary', 'onPrimary', 'primaryContainer', 'onPrimaryContainer', 'inversePrimary',
  'secondary', 'onSecondary', 'secondaryContainer', 'onSecondaryContainer',
  'tertiary', 'onTertiary', 'tertiaryContainer', 'onTertiaryContainer',
  'background', 'onBackground', 'surface', 'onSurface',
  'surfaceVariant', 'onSurfaceVariant', 'surfaceTint',
  'inverseSurface', 'inverseOnSurface',
  'error', 'onError', 'errorContainer', 'onErrorContainer',
  'outline', 'outlineVariant', 'scrim',
  'surfaceBright', 'surfaceDim', 'surfaceContainer', 'surfaceContainerHigh',
  'surfaceContainerHighest', 'surfaceContainerLow', 'surfaceContainerLowest',
];

const FROM_SCHEME = new Set([
  'primary', 'onPrimary', 'primaryContainer', 'onPrimaryContainer', 'inversePrimary',
  'secondary', 'onSecondary', 'secondaryContainer', 'onSecondaryContainer',
  'tertiary', 'onTertiary', 'tertiaryContainer', 'onTertiaryContainer',
  'background', 'onBackground', 'surface', 'onSurface',
  'surfaceVariant', 'onSurfaceVariant',
  'inverseSurface', 'inverseOnSurface',
  'error', 'onError', 'errorContainer', 'onErrorContainer',
  'outline', 'outlineVariant', 'scrim',
]);

const NEUTRAL_TONES = {
  light: { surfaceBright: 98, surfaceDim: 87, surfaceContainerLowest: 100, surfaceContainerLow: 96,
           surfaceContainer: 94, surfaceContainerHigh: 92, surfaceContainerHighest: 90 },
  dark: { surfaceBright: 24, surfaceDim: 6, surfaceContainerLowest: 4, surfaceContainerLow: 10,
          surfaceContainer: 12, surfaceContainerHigh: 17, surfaceContainerHighest: 22 },
};

const hex = (argb) => '0x' + (argb & 0x00FFFFFF).toString(16).padStart(6, '0').toUpperCase();
const outPath = process.argv[2] || path.join(__dirname, 'M3Palettes.kt');

const rows = [];
const defaultSeedHue = Hct.fromInt(0xFF4285F4).hue;

for (let hue = 0; hue < 360; hue += 10) {
  const theme = themeFromSourceColor(Hct.from(hue, 36, 40).toInt());
  const lj = theme.schemes.light.toJSON();
  const dj = theme.schemes.dark.toJSON();
  const row = [];
  for (const role of ROLES) {
    if (FROM_SCHEME.has(role)) {
      row.push(hex(lj[role]), hex(dj[role]));
    } else if (role === 'surfaceTint') {
      row.push(hex(lj.primary), hex(dj.primary));
    } else {
      row.push(
        hex(theme.palettes.neutral.tone(NEUTRAL_TONES.light[role])),
        hex(theme.palettes.neutral.tone(NEUTRAL_TONES.dark[role])),
      );
    }
  }
  rows.push(row.join(', '));
}

const kotlin = `package com.galstruo.app.ui.theme

/**
 * Material 3 官方 HCT 色调板表(由 material-color-utilities 0.2.0 生成,勿手改)。
 * 36 个色相(0°,10°,…,350°) × 36 个角色 × 浅色/深色两套。
 * 每行 72 个 ARGB 整数,排列为 [角色0浅, 角色0深, 角色1浅, 角色1深, …]。
 * 查询时相邻色相线性插值。
 */
internal val M3PaletteTable: Array<IntArray> = arrayOf(
${rows.map((r) => '    intArrayOf(' + r + ')').join(',\n')}
)

/** 角色顺序(与每行 72 个值对应) */
internal val M3PaletteRoles: List<String> = listOf(
    ${ROLES.map((r) => '"' + r + '"').join(', ')}
)

/** 默认种子色 0xFF4285F4 在 HCT 下的色相(设置里没选自定义色时用) */
internal const val DEFAULT_SEED_HUE = ${defaultSeedHue.toFixed(4)}f
`;

fs.writeFileSync(outPath, kotlin, 'utf8');
console.log('written:', outPath, '| rows =', rows.length, '| per-row ints =', rows[0].split(',').length);
console.log('defaultSeedHue =', defaultSeedHue.toFixed(4));
