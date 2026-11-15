package pcbuildmate.ui.preview

import kotlin.math.cos
import kotlin.math.sin

/**
 * 相机（右手系，Z 向上）：
 * - 世界：X = 主板法线，Y = 前后深度，Z = 高度
 * - yaw 绕 Z，pitch 绕 X；相机默认在 +X 侧看向 -X
 */
data class ViewPose(
    val yaw: Float = -1.35f,
    val pitch: Float = 0.35f,
    val distance: Float = 1200f,
    val focal: Float = 1.15f,
)

data class P2(val x: Float, val y: Float, val depth: Float)

enum class Face { TOP, BOTTOM, LEFT, RIGHT, FRONT, BACK }

data class FaceCorners(val face: Face, val normal: Vec3, val corners: List<Vec3>)

/**
 * 先 yaw 绕 Z（世界向上），再 pitch 绕 X（世界向右）。
 * 结果：屏幕右 = X'，屏幕上 = Z'，深度 = Y'。
 */
fun rotate(v: Vec3, yaw: Float, pitch: Float): Vec3 {
    val cy = cos(yaw)
    val sy = sin(yaw)
    val cp = cos(pitch)
    val sp = sin(pitch)
    // yaw around Z
    val x1 = v.x * cy - v.y * sy
    val y1 = v.x * sy + v.y * cy
    val z1 = v.z
    // pitch around X
    val y2 = y1 * cp - z1 * sp
    val z2 = y1 * sp + z1 * cp
    return Vec3(x1, y2, z2)
}

fun project(point: Vec3, sceneCenter: Vec3, pose: ViewPose, width: Float, height: Float): P2 {
    val v = rotate(
        Vec3(point.x - sceneCenter.x, point.y - sceneCenter.y, point.z - sceneCenter.z),
        pose.yaw,
        pose.pitch,
    )
    val depth = v.y + pose.distance
    val f = pose.focal * height
    return P2(
        x = width / 2f + v.x * f / depth,
        y = height / 2f - v.z * f / depth,
        depth = depth,
    )
}

/** 盒体 6 个面；法线在模型空间，交给 [isVisible] 旋转换算。 */
fun facesOf(box: Box3): List<FaceCorners> {
    val x0 = box.min.x
    val x1 = box.max.x
    val y0 = box.min.y
    val y1 = box.max.y
    val z0 = box.min.z
    val z1 = box.max.z
    fun p(x: Float, y: Float, z: Float) = Vec3(x, y, z)
    return listOf(
        FaceCorners(Face.TOP, Vec3(0f, 0f, 1f), listOf(p(x0, y0, z1), p(x1, y0, z1), p(x1, y1, z1), p(x0, y1, z1))),
        FaceCorners(Face.BOTTOM, Vec3(0f, 0f, -1f), listOf(p(x0, y1, z0), p(x1, y1, z0), p(x1, y0, z0), p(x0, y0, z0))),
        FaceCorners(Face.FRONT, Vec3(0f, -1f, 0f), listOf(p(x0, y0, z0), p(x1, y0, z0), p(x1, y0, z1), p(x0, y0, z1))),
        FaceCorners(Face.BACK, Vec3(0f, 1f, 0f), listOf(p(x1, y1, z0), p(x0, y1, z0), p(x0, y1, z1), p(x1, y1, z1))),
        FaceCorners(Face.LEFT, Vec3(-1f, 0f, 0f), listOf(p(x0, y1, z0), p(x0, y0, z0), p(x0, y0, z1), p(x0, y1, z1))),
        FaceCorners(Face.RIGHT, Vec3(1f, 0f, 0f), listOf(p(x1, y0, z0), p(x1, y1, z0), p(x1, y1, z1), p(x1, y0, z1))),
    )
}

/** 背面剔除：相机在 -Y 方向（旋转后），面朝相机则法线与视线反向。 */
fun isVisible(face: FaceCorners, pose: ViewPose, sceneCenter: Vec3): Boolean {
    val centroid = Vec3(
        face.corners.map { it.x }.average().toFloat(),
        face.corners.map { it.y }.average().toFloat(),
        face.corners.map { it.z }.average().toFloat(),
    )
    val rel = Vec3(centroid.x - sceneCenter.x, centroid.y - sceneCenter.y, centroid.z - sceneCenter.z)
    val n = rotate(face.normal, pose.yaw, pose.pitch)
    val c = rotate(rel, pose.yaw, pose.pitch)
    val toViewer = Vec3(c.x, c.y + pose.distance, c.z)
    val dot = n.x * toViewer.x + n.y * toViewer.y + n.z * toViewer.z
    return dot < 0f
}
