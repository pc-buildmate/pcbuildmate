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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.ui.R
import pcbuildmate.contracts.CataloguePort
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 3D 间隙预览：轴对齐盒体 + 程序化纹理，表达尺寸与间隙，不做产品外观。
 *
 * 坐标系见 docs/18-3d-coordinates.md；机箱是半透明 ghost，可隐藏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Preview3DScreen(
    revision: BuildRevision?,
    catalogue: CataloguePort,
    onBack: () -> Unit,
) {
    val scene = remember(revision, catalogue) { SceneBuilder.build(revision, catalogue) }
    var yaw by rememberSaveable { mutableStateOf(-1.35f) }
    var pitch by rememberSaveable { mutableStateOf(0.35f) }
    var zoom by rememberSaveable { mutableStateOf(1f) }
    var showCase by rememberSaveable { mutableStateOf(true) }
    val visibleScene = remember(scene, showCase) {
        scene?.let {
            if (showCase) it
            else it.copy(boxes = it.boxes.filterNot { b -> b.style == BoxStyle.CASE })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.preview3d_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } },
                actions = {
                    TextButton(onClick = { showCase = !showCase }) {
                        Text(if (showCase) stringResource(R.string.preview3d_hide_case) else stringResource(R.string.preview3d_show_case))
                    }
                    TextButton(onClick = {
                        yaw = -1.35f
                        pitch = 0.35f
                        zoom = 1f
                    }) { Text(stringResource(R.string.preview3d_reset)) }
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
                Text(stringResource(R.string.preview3d_empty))
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
                            pitch = clampPitch(pitch + drag.y * 0.008f)
                        }
                    },
            ) {
                val pose = ViewPose(
                    yaw = yaw,
                    pitch = pitch,
                    distance = visibleScene.radius * 3.1f / zoom,
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
                    AssistChip(
                        onClick = {
                            yaw = -1.5708f
                            pitch = clampPitch(0f)
                            zoom = 1f
                        },
                        label = { Text(stringResource(R.string.preview3d_view_board), style = MaterialTheme.typography.labelSmall) },
                    )
                    AssistChip(
                        onClick = {
                            yaw = 0f
                            pitch = clampPitch(1.5708f)
                            zoom = 1f
                        },
                        label = { Text(stringResource(R.string.preview3d_view_top), style = MaterialTheme.typography.labelSmall) },
                    )
                    AssistChip(
                        onClick = {
                            yaw = 0f
                            pitch = clampPitch(0f)
                            zoom = 1f
                        },
                        label = { Text(stringResource(R.string.preview3d_view_rear), style = MaterialTheme.typography.labelSmall) },
                    )
                }
                Spacer(Modifier.height(6.dp))
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
                    stringResource(R.string.preview3d_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val lightDir = Vec3(0.35f, 0.75f, -0.55f)

private fun DrawScope.drawScene(scene: Scene3d, pose: ViewPose) {
    fun projectAll(points: List<Vec3>): List<P2> =
        points.map { project(it, scene.center, pose, size.width, size.height) }

    fun pathOf(pts: List<P2>): Path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        close()
    }

    // 1) 机箱 ghost：先画、不参与排序，避免遮住零件
    scene.boxes.filter { it.ghost }.forEach { box ->
        facesOf(box).filter { isVisible(it, pose, scene.center) }.forEach { face ->
            val path = pathOf(projectAll(face.corners))
            drawPath(path, Color(0x12FFFFFF))
            drawPath(path, Color(0x887AD1FF), style = Stroke(width = 1.6f))
        }
    }

    // 2) 限制面：机箱限长 / 侧板限高
    scene.limits.forEach { limit ->
        val pts = projectAll(limit.corners)
        val color = if (limit.conflict) Color(0xFFFF5252) else Color(0xFF7AD1FF)
        drawPath(pathOf(pts), color.copy(alpha = 0.10f))
        for (i in pts.indices) {
            dashedLine(pts[i], pts[(i + 1) % pts.size], color.copy(alpha = 0.8f))
        }
    }

    // 3) 实体零件：远到近
    val faces = scene.boxes.filterNot { it.ghost }.flatMap { box ->
        facesOf(box)
            .filter { isVisible(it, pose, scene.center) }
            .map { face ->
                val projected = projectAll(face.corners)
                Triple(box, face, projected)
            }
    }.sortedByDescending { (_, _, pts) -> pts.map { it.depth }.average() }

    faces.forEach { (box, face, pts) ->
        val path = pathOf(pts)
        val shade = shadeOf(face, pose)
        drawPath(path, baseColor(box, face, shade))
        clipPath(path) { textures(box, face, pts) }
        drawPath(
            path,
            if (box.conflict) Color(0xFFFF5252) else Color(0x66000000),
            style = Stroke(width = if (box.conflict) 3f else 1.4f),
        )
    }
}

private fun DrawScope.dashedLine(a: P2, b: P2, color: Color) {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len = kotlin.math.sqrt(dx * dx + dy * dy)
    if (len < 1f) return
    val segments = (len / 8f).toInt().coerceAtLeast(1)
    for (i in 0 until segments step 2) {
        val t0 = i.toFloat() / segments
        val t1 = ((i + 1).toFloat() / segments).coerceAtMost(1f)
        drawLine(
            color,
            Offset(a.x + dx * t0, a.y + dy * t0),
            Offset(a.x + dx * t1, a.y + dy * t1),
            strokeWidth = 1.4f,
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
        BoxStyle.GPU -> when (face.face) {
            Face.TOP -> Color(0xFF4A505A)
            Face.BOTTOM -> Color(0xFF23262B)
            else -> Color(0xFF1C4A32)
        }
        BoxStyle.COOLER -> Color(0xFF9AA3AE)
        BoxStyle.IO -> Color(0xFF5A616B)
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

private fun DrawScope.textures(box: Box3, face: FaceCorners, pts: List<P2>) {
    // 纹理必须贴在面所在平面上：用双线性插值把 (u,v) 映射到投影后的四边形，
    // 这样转动时线条跟着面走，而不是固定在屏幕坐标轴方向。
    fun p(u: Float, v: Float): Offset = bilinear(pts, u, v)
    fun line(u0: Float, v0: Float, u1: Float, v1: Float, color: Color, width: Float) {
        drawLine(color, p(u0, v0), p(u1, v1), strokeWidth = width)
    }

    val faint = Color(0x22FFFFFF)
    val ink = Color(0x3AFFFFFF)
    when (box.style) {
        BoxStyle.CASE -> Unit // ghost 单独绘制

        BoxStyle.BOARD -> {
            // 横向：PCIe/内存槽示意；纵向：板面走线
            repeat(6) { i ->
                val v = (i + 1) / 7f
                line(0.06f, v, 0.94f, v, Color(0x66D4AF37), 2.4f)
            }
            repeat(4) { i ->
                val u = (i + 1) / 5f
                line(u, 0.08f, u, 0.92f, faint, 1.4f)
            }
        }

        BoxStyle.GPU -> when (face.face) {
            // 背板：纵向鳍片（沿长度方向）
            Face.TOP -> repeat(7) { i ->
                val v = (i + 1) / 8f
                line(0.08f, v, 0.92f, v, Color(0x55FFFFFF), 2f)
            }
            // 底面朝下，只留淡淡的走线，不做风扇贴图
            Face.BOTTOM -> repeat(5) { i ->
                val u = (i + 1) / 6f
                line(u, 0.1f, u, 0.9f, faint, 1.2f)
            }
            // 侧面：PCB 走线
            else -> repeat(6) { i ->
                val v = (i + 1) / 7f
                line(0.05f, v, 0.95f, v, faint, 1.2f)
            }
        }

        BoxStyle.COOLER -> {
            repeat(9) { i ->
                val v = (i + 1) / 10f
                line(0.06f, v, 0.94f, v, Color(0x77202428), 1.8f)
            }
        }

        BoxStyle.IO -> {
            repeat(6) { i ->
                val u = (i + 1) / 7f
                line(u, 0.1f, u, 0.9f, Color(0x55000000), 1.6f)
            }
        }
    }
    // 面边缘高光，帮助辨认轮廓
    drawLine(Color(0x22FFFFFF), p(0f, 0f), p(1f, 0f), strokeWidth = 2f)
}

/** 四边形 (p0,p1,p2,p3) 的双线性插值；用于把纹理钉在面所在平面上。 */
internal fun bilinear(pts: List<P2>, u: Float, v: Float): Offset {
    val p0 = pts[0]
    val p1 = pts[1]
    val p2 = pts[2]
    val p3 = pts[3]
    val x = (1 - u) * (1 - v) * p0.x + u * (1 - v) * p1.x + u * v * p2.x + (1 - u) * v * p3.x
    val y = (1 - u) * (1 - v) * p0.y + u * (1 - v) * p1.y + u * v * p2.y + (1 - u) * v * p3.y
    return Offset(x, y)
}

private fun DrawScope.drawGroundGrid(scene: Scene3d, pose: ViewPose) {
    val span = scene.radius * 1.6f
    val step = span / 8f
    val color = Color(0x1A7AD1FF)
    val c = scene.center
    val x0 = c.x - span / 2f
    val y0 = c.y - span / 2f
    var x = x0
    while (x <= x0 + span) {
        val a = project(Vec3(x, y0, 0f), c, pose, size.width, size.height)
        val b = project(Vec3(x, y0 + span, 0f), c, pose, size.width, size.height)
        drawLine(color, Offset(a.x, a.y), Offset(b.x, b.y), strokeWidth = 1f)
        x += step
    }
    var y = y0
    while (y <= y0 + span) {
        val a = project(Vec3(x0, y, 0f), c, pose, size.width, size.height)
        val b = project(Vec3(x0 + span, y, 0f), c, pose, size.width, size.height)
        drawLine(color, Offset(a.x, a.y), Offset(b.x, b.y), strokeWidth = 1f)
        y += step
    }
}
