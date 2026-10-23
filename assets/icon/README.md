# App 图标

![预览](preview.png)

## 设计
- 风格：扁平、无阴影、无渐变。
- 构成：绿色底 + 白色足球 + 右下角红色“禁止”角标，表示“去掉懂球帝广告”。
- 角标外有一圈底色缝隙，把它和足球分开。

## 配色
| 用途 | 颜色 |
| --- | --- |
| 背景 | `#16A34A` |
| 足球 / 角标符号 | `#FFFFFF` |
| 角标底 | `#FF4D4F` |

## 尺寸（108dp 自适应图标画布）
- 足球：圆心 (54, 54)（画布正中），半径 24，边缘留 3 宽白圈（盖住绿色块的边，避免抗锯齿细白线）。
- 角标：圆心 (70, 70)，半径 10.5，外圈缝隙 2.5。
- 两者都在中心直径 66dp 的安全区内，圆形、圆角方形等各种遮罩都不会裁到。

## 文件
| 文件 | 说明 |
| --- | --- |
| `icon.svg` | 矢量源文件 |
| `preview.png` | 432px 预览 |
| `preview_masked.png` | 圆角方形 / 圆形遮罩下的效果 |
| `android/res/drawable/ic_launcher_foreground.xml` | 自适应图标前景 |
| `android/res/drawable/ic_launcher_monochrome.xml` | 主题图标（单色，目前只是轮廓，待细化） |
| `android/res/values/ic_launcher_colors.xml` | 背景色 |
| `android/res/mipmap-anydpi-v26/ic_launcher*.xml` | 自适应图标入口 |
| `gen_icon.py` | 生成以上所有文件的脚本 |

## 重新生成
在项目根目录运行：

```bash
python -I -X utf8 assets/icon/gen_icon.py .
```

预览图用 Inkscape 渲染：

```bash
inkscape assets/icon/icon.svg -o assets/icon/preview.png -w 432
```

接入工程时把 `android/res/` 下的内容拷进 `app/src/main/res/`。
