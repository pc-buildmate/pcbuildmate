package pcbuildmate.ui.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.CataloguePort
import kotlin.math.min

/**
 * 3D 间隙预览：用轴对齐盒体 + 程序化纹理表达尺寸/间隙，不做产品外观。
 *
 * 数据：只有目录字段才画真实尺寸；缺失字段用常量并标注（见 SceneBuilder.notes）。
 * 交互：拖动旋转、重置视角；红框表示超出机箱上限。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Preview3DScreen(
    revision: BuildRevision?,
    catalogue: CataloguePort,
    onBack: () -> Unit,
) {
    val scene = remember(revision, catalogue) { SceneBuilder.build(revision, catalogue) }
    var yaw by rememberSaveable { mutableStateOf(0.72f) }
    var pitch by rememberSaveable { mutableStateOf(0.32f) }
    var zoom by rememberSaveable { mutableStateOf(1f) }
    var showCase by rememberSaveable { mutableStateOf(true) }
    val visibleScene = remember(scene, showCase) {
        scene?.let { if (showCase) it else it.copy(boxes = it.boxes.filterNot { b -> b.style == BoxStyle.CASE }) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("3D 间隙预览") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = {
                    TextButton(onClick = { showCase = !showCase }) {
                        Text(if (showCase) "隐藏机箱" else "显示机箱")
                    }
                    TextButton(onClick = {
                        yaw = 0.72f
                        pitch = 0.32f
                        zoom = 1f
                    }) { Text("重置视角") }
                },
            )
        },
    ) { padding ->
        if (visibleScene == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
            ) {
                Text("还没有配置，先载入演示配置或生成一份配置。")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF10141A))
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            yaw += drag.x * 0.010f
                            pitch = (pitch + drag.y * 0.008f).coerceIn(-0.9f, 0.9f)
                        }
                    },
            ) {
                val pose = ViewPose(
                    yaw = yaw,
                    pitch = pitch,
                    distance = visibleScene.maxExtentMm * 2.7f / zoom,
                )
                drawGroundGrid(visibleScene, pose)
                drawScene(visibleScene, pose)
            }

            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    visibleScene.labels.forEach { label ->
                        AssistChip(
                            onClick = {},
                            label = { Text(label.text, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                visibleScene.notes.forEach { note ->
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "拖动旋转 · 盒体为尺寸示意，非外观模型",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val lightDir = Vec3(0.35f, 0.75f, -0.55f)

private fun DrawScope.drawScene(scene: Scene3d, pose: ViewPose) {
    val center = Vec3(scene.maxExtentMm / 2f, scene.maxExtentMm * 0.42f, 0f)
    fun facesOfBoxes(boxes: List<Box3>): List<Triple<Box3, FaceCorners, List<P2>>> =
        boxes.flatMap { box ->
            facesOf(box)
                .filter { isVisible(it, pose, center) }
                .map { face ->
                    val projected = face.corners.map { project(it, center, pose, size.width, size.height) }
                    Triple(box, face, projected)
                }
        }

    // 机箱是"允许空间"容器：先画半透明轮廓，避免遮住内部零件。
    val ghostFaces = facesOfBoxes(scene.boxes.filter { it.ghost })
    ghostFaces.forEach { (_, _, pts) ->
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            close()
        }
        drawPath(path, Color(0x0FFFFFFF))
        drawPath(path, Color(0x887AD1FF), style = Stroke(width = 1.6f))
    }

    val faces = facesOfBoxes(scene.boxes.filterNot { it.ghost })
        .sortedByDescending { (_, _, pts) -> pts.map { it.depth }.average() }

    faces.forEach { (box, face, pts) ->
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            close()
        }
        val shade = shadeOf(face, pose)
        val base = baseColor(box, face, shade)
        drawPath(path, base)
        clipPath(path) { textures(box, face, pts, shade) }
        drawPath(
            path,
            if (box.conflict) Color(0xFFFF5252) else Color(0x66000000),
            style = Stroke(width = if (box.conflict) 3f else 1.4f),
        )
    }
}

private fun shadeOf(face: FaceCorners, pose: ViewPose): Float {
    val n = rotate(face.normal, pose.yaw, pose.pitch)
    val dot = n.x * lightDir.x + n.y * lightDir.y + n.z * lightDir.z
    return (0.42f + 0.58f * dot.coerceAtLeast(0f)).coerceIn(0.35f, 1.05f)
}

private fun baseColor(box: Box3, face: FaceCorners, shade: Float): Color {
    val base = when (box.style) {
        BoxStyle.CASE -> Color(0xFF39414D)
        BoxStyle.BOARD -> Color(0xFF20342A)
        BoxStyle.GPU -> if (face.face == Face.TOP) Color(0xFF4A505A) else Color(0xFF1C4A32)
        BoxStyle.COOLER -> Color(0xFF9AA3AE)
    }
    val tint = if (box.conflict) Color(0xFFB23A3A) else base
    return lerp(Color.Black, tint, shade.coerceIn(0.55f, 1f))
}

private fun lerp(from: Color, to: Color, t: Float): Color = Color(
    red = from.red + (to.red - from.red) * t,
    green = from.green + (to.green - from.green) * t,
    blue = from.blue + (to.blue - from.blue) * t,
    alpha = from.alpha + (to.alpha - from.alpha) * t,
)

private fun DrawScope.textures(box: Box3, face: FaceCorners, pts: List<P2>, shade: Float) {
    val xs = pts.map { it.x }
    val ys = pts.map { it.y }
    val left = xs.min()
    val right = xs.max()
    val top = ys.min()
    val bottom = ys.max()
    val w = right - left
    val h = bottom - top
    if (w < 6f || h < 6f) return

    val ink = Color(0x33FFFFFF)
    val strong = Color(0x55FFFFFF)
    when (box.style) {
        BoxStyle.CASE -> {
            if (face.face == Face.TOP || face.face == Face.BOTTOM) {
                val step = h / 8f
                var y = top
                while (y < bottom) {
                    drawLine(ink, Offset(left, y), Offset(right, y), strokeWidth = 1f)
                    y += step
                }
            }
            val vStep = w / 10f
            var x = left
            while (x < right) {
                drawLine(ink, Offset(x, top), Offset(x, bottom), strokeWidth = 1f)
                x += vStep
            }
        }

        BoxStyle.BOARD -> {
            repeat(5) { i ->
                val y = top + h * (i + 1) / 6f
                drawLine(Color(0x66D4AF37), Offset(left + w * 0.08f, y), Offset(right - w * 0.08f, y), strokeWidth = 2.2f)
            }
        }

        BoxStyle.GPU -> {
            if (face.face == Face.TOP) {
                repeat(7) { i ->
                    val x = left + w * (i + 1) / 8f
                    drawLine(strong, Offset(x, top + h * 0.12f), Offset(x, bottom - h * 0.12f), strokeWidth = 2f)
                }
            } else {
                val r = min(w, h) * 0.22f
                val cy = top + h * 0.5f
                listOf(0.3f, 0.7f).forEach { fx ->
                    val cx = left + w * fx
                    drawCircle(Color(0xCC1A1D21), radius = r, center = Offset(cx, cy))
                    drawCircle(Color(0x55FFFFFF), radius = r * 0.82f, center = Offset(cx, cy), style = Stroke(1.6f))
                    repeat(7) { blade ->
                        val a = blade * (6.2832f / 7f)
                        drawLine(
                            Color(0x66FFFFFF),
                            Offset(cx, cy),
                            Offset(cx + ((r * 0.78f) * kotlin.math.cos(a)), cy + ((r * 0.78f) * kotlin.math.sin(a))),
                            strokeWidth = 1.4f,
                        )
                    }
                }
            }
        }

        BoxStyle.COOLER -> {
            val step = h / 14f
            var y = top
            while (y < bottom) {
                drawLine(Color(0x77202428), Offset(left + w * 0.05f, y), Offset(right - w * 0.05f, y), strokeWidth = 1.6f)
                y += step
            }
        }
    }
    // 顶部高光，让盒体不像纯色贴片
    drawLine(
        Color(0x22FFFFFF),
        Offset(left, top),
        Offset(right, top),
        strokeWidth = 2f,
    )
}

private fun DrawScope.drawGroundGrid(scene: Scene3d, pose: ViewPose) {
    val center = Vec3(scene.maxExtentMm / 2f, scene.maxExtentMm * 0.42f, 0f)
    val span = scene.maxExtentMm * 1.4f
    val step = span / 8f
    val color = Color(0x1A7AD1FF)
    var x = -span / 2f
    while (x <= span) {
        val a = project(Vec3(scene.maxExtentMm / 2f + x, 0f, -span / 2f), center, pose, size.width, size.height)
        val b = project(Vec3(scene.maxExtentMm / 2f + x, 0f, span / 2f), center, pose, size.width, size.height)
        drawLine(color, Offset(a.x, a.y), Offset(b.x, b.y), strokeWidth = 1f)
        x += step
    }
    var z = -span / 2f
    while (z <= span) {
        val a = project(Vec3(scene.maxExtentMm / 2f - span / 2f, 0f, z), center, pose, size.width, size.height)
        val b = project(Vec3(scene.maxExtentMm / 2f + span / 2f, 0f, z), center, pose, size.width, size.height)
        drawLine(color, Offset(a.x, a.y), Offset(b.x, b.y), strokeWidth = 1f)
        z += step
    }
}
