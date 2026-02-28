package com.zionchat.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {
    // 汉堡菜单图标
    val HamburgerMenu = ImageVector.Builder(
        name = "hamburger_menu",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF1C1C1E)),
            stroke = null,
            strokeLineWidth = 0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(4f, 8f)
            horizontalLineToRelative(16f)
            arcToRelative(1f, 1f, 0f, true, true, 0f, -2f)
            horizontalLineTo(4f)
            arcToRelative(1f, 1f, 0f, true, false, 0f, -2f)
            close()
            moveTo(4f, 13f)
            horizontalLineToRelative(10f)
            arcToRelative(1f, 1f, 0f, true, false, 0f, -2f)
            horizontalLineTo(4f)
            arcToRelative(1f, 1f, 0f, true, false, 0f, -2f)
            close()
        }
    }.build()

    // 新建对话图标（编辑图标）
    val NewChat = ImageVector.Builder(
        name = "new_chat",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF000000)),
            stroke = null
        ) {
            moveTo(15.673f, 3.913f)
            curveTo(16.892f, 2.694f, 18.868f, 2.694f, 20.087f, 3.913f)
            curveTo(21.306f, 5.132f, 21.306f, 7.108f, 20.087f, 8.327f)
            lineTo(14.15f, 14.264f)
            curveTo(13.385f, 15.029f, 12.392f, 15.525f, 11.321f, 15.678f)
            lineTo(9.141f, 15.99f)
            curveTo(8.83f, 16.034f, 8.515f, 15.929f, 8.293f, 15.707f)
            curveTo(8.07f, 15.484f, 7.966f, 15.17f, 8.01f, 14.858f)
            lineTo(8.321f, 12.678f)
            curveTo(8.474f, 11.607f, 8.971f, 10.615f, 9.736f, 9.85f)
            lineTo(15.673f, 3.913f)
            close()
            moveTo(18.673f, 5.327f)
            curveTo(18.235f, 4.889f, 17.525f, 4.889f, 17.087f, 5.327f)
            lineTo(11.15f, 11.264f)
            curveTo(10.691f, 11.723f, 10.393f, 12.319f, 10.301f, 12.961f)
            lineTo(10.179f, 13.821f)
            lineTo(11.039f, 13.698f)
            curveTo(11.681f, 13.607f, 12.277f, 13.309f, 12.736f, 12.85f)
            lineTo(18.673f, 6.913f)
            curveTo(19.111f, 6.475f, 19.111f, 5.765f, 18.673f, 5.327f)
            close()
            moveTo(11f, 3.999f)
            curveTo(11f, 4.551f, 10.553f, 5f, 10.001f, 5f)
            curveTo(9.002f, 5.001f, 8.298f, 5.008f, 7.747f, 5.061f)
            curveTo(7.207f, 5.112f, 6.885f, 5.201f, 6.638f, 5.327f)
            curveTo(6.074f, 5.614f, 5.615f, 6.073f, 5.327f, 6.638f)
            curveTo(5.193f, 6.901f, 5.101f, 7.249f, 5.051f, 7.854f)
            curveTo(5.001f, 8.471f, 5f, 9.263f, 5f, 10.4f)
            verticalLineTo(13.6f)
            curveTo(5f, 14.736f, 5.001f, 15.529f, 5.051f, 16.146f)
            curveTo(5.101f, 16.751f, 5.193f, 17.098f, 5.327f, 17.362f)
            curveTo(5.615f, 17.926f, 6.074f, 18.385f, 6.638f, 18.673f)
            curveTo(6.901f, 18.807f, 7.249f, 18.899f, 7.854f, 18.949f)
            curveTo(8.471f, 18.999f, 9.263f, 19f, 10.4f, 19f)
            horizontalLineTo(13.6f)
            curveTo(14.737f, 19f, 15.529f, 18.999f, 16.146f, 18.949f)
            curveTo(16.751f, 18.899f, 17.099f, 18.807f, 17.362f, 18.673f)
            curveTo(17.927f, 18.385f, 18.385f, 17.926f, 18.673f, 17.362f)
            curveTo(18.799f, 17.115f, 18.888f, 16.793f, 18.939f, 16.253f)
            curveTo(18.992f, 15.702f, 18.999f, 14.998f, 19f, 13.999f)
            curveTo(19f, 13.447f, 19.448f, 12.999f, 20.001f, 13f)
            curveTo(20.553f, 13f, 21f, 13.448f, 21f, 14.001f)
            curveTo(20.999f, 14.979f, 20.993f, 15.781f, 20.93f, 16.442f)
            curveTo(20.866f, 17.116f, 20.739f, 17.713f, 20.455f, 18.27f)
            curveTo(19.976f, 19.211f, 19.211f, 19.976f, 18.27f, 20.455f)
            curveTo(17.678f, 20.757f, 17.038f, 20.882f, 16.309f, 20.942f)
            curveTo(15.601f, 21f, 14.727f, 21f, 13.643f, 21f)
            horizontalLineTo(10.357f)
            curveTo(9.273f, 21f, 8.399f, 21f, 7.691f, 20.942f)
            curveTo(6.963f, 20.882f, 6.322f, 20.757f, 5.73f, 20.455f)
            curveTo(4.789f, 19.976f, 4.024f, 19.211f, 3.545f, 18.27f)
            curveTo(3.243f, 17.677f, 3.117f, 17.037f, 3.058f, 16.309f)
            curveTo(3f, 15.601f, 3f, 14.726f, 3f, 13.643f)
            verticalLineTo(10.357f)
            curveTo(3f, 9.273f, 3f, 8.399f, 3.058f, 7.691f)
            curveTo(3.117f, 6.962f, 3.243f, 6.322f, 3.545f, 5.73f)
            curveTo(4.024f, 4.789f, 4.789f, 4.024f, 5.73f, 3.545f)
            curveTo(6.286f, 3.261f, 6.884f, 3.133f, 7.557f, 3.069f)
            curveTo(8.219f, 3.007f, 9.021f, 3.001f, 9.999f, 3f)
            curveTo(10.552f, 3f, 11f, 3.447f, 11f, 3.999f)
            close()
        }
    }.build()

    // ChatGPT Logo 图标
    val ChatGPTLogo = ImageVector.Builder(
        name = "chatgpt_logo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF1C1C1E)),
            stroke = null
        ) {
            moveTo(12.18f, 11.818f)
            curveTo(12.295f, 12.638f, 11.723f, 13.397f, 10.903f, 13.512f)
            curveTo(10.083f, 13.627f, 9.324f, 13.056f, 9.209f, 12.235f)
            curveTo(9.094f, 11.415f, 9.665f, 10.657f, 10.485f, 10.541f)
            curveTo(11.306f, 10.426f, 12.064f, 10.998f, 12.18f, 11.818f)
            close()
            moveTo(14.279f, 1.707f)
            lineTo(17.736f, 2.132f)
            curveTo(18.535f, 2.23f, 19.194f, 2.311f, 19.726f, 2.421f)
            curveTo(20.278f, 2.535f, 20.779f, 2.696f, 21.231f, 3f)
            curveTo(21.931f, 3.473f, 22.464f, 4.154f, 22.753f, 4.948f)
            curveTo(22.939f, 5.46f, 22.974f, 5.985f, 22.951f, 6.548f)
            curveTo(22.929f, 7.091f, 22.848f, 7.75f, 22.75f, 8.549f)
            lineTo(22.326f, 12.006f)
            curveTo(22.228f, 12.805f, 22.146f, 13.464f, 22.037f, 13.996f)
            curveTo(21.923f, 14.549f, 21.762f, 15.049f, 21.457f, 15.501f)
            curveTo(20.985f, 16.201f, 20.303f, 16.734f, 19.509f, 17.023f)
            curveTo(19.428f, 17.052f, 19.345f, 17.079f, 19.262f, 17.101f)
            curveTo(18.729f, 17.246f, 18.18f, 16.931f, 18.035f, 16.398f)
            curveTo(17.89f, 15.865f, 18.205f, 15.316f, 18.738f, 15.171f)
            curveTo(18.769f, 15.163f, 18.797f, 15.154f, 18.825f, 15.144f)
            curveTo(19.222f, 14.999f, 19.563f, 14.733f, 19.799f, 14.382f)
            curveTo(19.897f, 14.237f, 19.989f, 14.022f, 20.078f, 13.592f)
            curveTo(20.169f, 13.149f, 20.241f, 12.571f, 20.346f, 11.721f)
            lineTo(20.76f, 8.347f)
            curveTo(20.864f, 7.496f, 20.934f, 6.918f, 20.953f, 6.467f)
            curveTo(20.971f, 6.028f, 20.934f, 5.797f, 20.874f, 5.632f)
            curveTo(20.729f, 5.235f, 20.463f, 4.894f, 20.112f, 4.658f)
            curveTo(19.967f, 4.56f, 19.752f, 4.468f, 19.322f, 4.379f)
            curveTo(18.879f, 4.288f, 18.302f, 4.216f, 17.451f, 4.112f)
            lineTo(14.077f, 3.698f)
            curveTo(13.226f, 3.593f, 12.648f, 3.523f, 12.197f, 3.505f)
            curveTo(11.758f, 3.487f, 11.527f, 3.524f, 11.362f, 3.584f)
            curveTo(10.965f, 3.728f, 10.624f, 3.995f, 10.388f, 4.345f)
            curveTo(10.321f, 4.444f, 10.257f, 4.576f, 10.196f, 4.784f)
            curveTo(10.039f, 5.314f, 9.482f, 5.616f, 8.953f, 5.459f)
            curveTo(8.423f, 5.302f, 8.121f, 4.745f, 8.278f, 4.216f)
            curveTo(8.383f, 3.862f, 8.523f, 3.533f, 8.73f, 3.226f)
            curveTo(9.203f, 2.526f, 9.884f, 1.993f, 10.678f, 1.704f)
            curveTo(11.19f, 1.518f, 11.715f, 1.483f, 12.278f, 1.506f)
            curveTo(12.821f, 1.528f, 13.48f, 1.609f, 14.279f, 1.707f)
            close()
            moveTo(11.76f, 8.8f)
            curveTo(11.309f, 8.819f, 10.731f, 8.889f, 9.881f, 8.993f)
            lineTo(6.506f, 9.408f)
            curveTo(5.656f, 9.512f, 5.078f, 9.584f, 4.636f, 9.675f)
            curveTo(4.205f, 9.764f, 3.99f, 9.856f, 3.845f, 9.954f)
            curveTo(3.495f, 10.19f, 3.228f, 10.531f, 3.084f, 10.928f)
            curveTo(3.024f, 11.093f, 2.987f, 11.323f, 3.005f, 11.763f)
            curveTo(3.023f, 12.214f, 3.093f, 12.792f, 3.198f, 13.642f)
            lineTo(3.475f, 15.903f)
            curveTo(3.684f, 15.676f, 3.877f, 15.475f, 4.059f, 15.3f)
            curveTo(4.45f, 14.926f, 4.85f, 14.616f, 5.335f, 14.418f)
            curveTo(6.09f, 14.109f, 6.922f, 14.039f, 7.719f, 14.216f)
            curveTo(8.231f, 14.33f, 8.676f, 14.568f, 9.124f, 14.872f)
            curveTo(9.555f, 15.163f, 10.05f, 15.56f, 10.649f, 16.039f)
            lineTo(14.314f, 18.971f)
            curveTo(14.455f, 18.798f, 14.566f, 18.603f, 14.644f, 18.391f)
            curveTo(14.704f, 18.226f, 14.741f, 17.995f, 14.723f, 17.556f)
            curveTo(14.705f, 17.105f, 14.634f, 16.527f, 14.53f, 15.676f)
            lineTo(14.116f, 12.302f)
            curveTo(14.011f, 11.452f, 13.939f, 10.874f, 13.848f, 10.432f)
            curveTo(13.759f, 10.001f, 13.667f, 9.786f, 13.569f, 9.641f)
            curveTo(13.333f, 9.29f, 12.992f, 9.024f, 12.595f, 8.88f)
            curveTo(12.43f, 8.82f, 12.2f, 8.782f, 11.76f, 8.8f)
            close()
            moveTo(12.143f, 19.795f)
            lineTo(9.431f, 17.626f)
            curveTo(8.793f, 17.115f, 8.36f, 16.77f, 8.003f, 16.528f)
            curveTo(7.657f, 16.293f, 7.45f, 16.206f, 7.283f, 16.169f)
            curveTo(6.885f, 16.08f, 6.469f, 16.115f, 6.092f, 16.269f)
            curveTo(5.934f, 16.334f, 5.745f, 16.455f, 5.443f, 16.745f)
            curveTo(5.131f, 17.043f, 4.762f, 17.456f, 4.219f, 18.066f)
            lineTo(3.813f, 18.523f)
            curveTo(3.834f, 18.654f, 3.856f, 18.775f, 3.879f, 18.887f)
            curveTo(3.968f, 19.318f, 4.06f, 19.533f, 4.158f, 19.678f)
            curveTo(4.394f, 20.028f, 4.735f, 20.295f, 5.132f, 20.439f)
            curveTo(5.297f, 20.499f, 5.528f, 20.536f, 5.967f, 20.518f)
            curveTo(6.418f, 20.5f, 6.996f, 20.43f, 7.847f, 20.326f)
            lineTo(11.221f, 19.911f)
            curveTo(11.572f, 19.868f, 11.876f, 19.831f, 12.143f, 19.795f)
            close()
            moveTo(11.679f, 6.802f)
            curveTo(12.242f, 6.779f, 12.767f, 6.814f, 13.279f, 7f)
            curveTo(14.073f, 7.289f, 14.755f, 7.822f, 15.227f, 8.522f)
            curveTo(15.532f, 8.974f, 15.693f, 9.475f, 15.807f, 10.027f)
            curveTo(15.917f, 10.559f, 15.998f, 11.218f, 16.096f, 12.017f)
            lineTo(16.52f, 15.474f)
            curveTo(16.618f, 16.273f, 16.699f, 16.932f, 16.721f, 17.475f)
            curveTo(16.744f, 18.038f, 16.709f, 18.563f, 16.523f, 19.075f)
            curveTo(16.234f, 19.869f, 15.701f, 20.551f, 15.001f, 21.023f)
            curveTo(14.549f, 21.327f, 14.049f, 21.488f, 13.496f, 21.602f)
            curveTo(12.964f, 21.712f, 12.305f, 21.793f, 11.506f, 21.891f)
            lineTo(8.049f, 22.316f)
            curveTo(7.25f, 22.414f, 6.591f, 22.495f, 6.048f, 22.517f)
            curveTo(5.485f, 22.54f, 4.96f, 22.505f, 4.448f, 22.319f)
            curveTo(3.654f, 22.03f, 2.973f, 21.497f, 2.5f, 20.797f)
            curveTo(2.196f, 20.345f, 2.035f, 19.844f, 1.921f, 19.292f)
            curveTo(1.811f, 18.76f, 1.73f, 18.101f, 1.632f, 17.302f)
            lineTo(1.207f, 13.845f)
            curveTo(1.109f, 13.046f, 1.028f, 12.387f, 1.006f, 11.844f)
            curveTo(0.983f, 11.281f, 1.018f, 10.756f, 1.204f, 10.244f)
            curveTo(1.493f, 9.45f, 2.026f, 8.768f, 2.726f, 8.296f)
            curveTo(3.178f, 7.991f, 3.679f, 7.83f, 4.231f, 7.716f)
            curveTo(4.763f, 7.607f, 5.422f, 7.526f, 6.221f, 7.428f)
            lineTo(9.678f, 7.003f)
            curveTo(10.477f, 6.905f, 11.136f, 6.824f, 11.679f, 6.802f)
            close()
        }
    }.build()

    // Apps 图标 - 与 HTML icons.css 一致
    val Apps = ImageVector.Builder(
        name = "apps",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF000000)),
            stroke = null,
            pathFillType = PathFillType.NonZero
        ) {
            // Top-left circle
            moveTo(6.75f, 4.5f)
            arcTo(2.25f, 2.25f, 0f, true, false, 6.75f, 9f)
            arcTo(2.25f, 2.25f, 0f, true, false, 6.75f, 4.5f)
            close()
            // Top-right circle
            moveTo(17.25f, 4.5f)
            arcTo(2.25f, 2.25f, 0f, true, false, 17.25f, 9f)
            arcTo(2.25f, 2.25f, 0f, true, false, 17.25f, 4.5f)
            close()
            // Bottom-left circle
            moveTo(6.75f, 15f)
            arcTo(2.25f, 2.25f, 0f, true, false, 6.75f, 19.5f)
            arcTo(2.25f, 2.25f, 0f, true, false, 6.75f, 15f)
            close()
            // Bottom-right circle
            moveTo(17.25f, 15f)
            arcTo(2.25f, 2.25f, 0f, true, false, 17.25f, 19.5f)
            arcTo(2.25f, 2.25f, 0f, true, false, 17.25f, 15f)
            close()
        }
    }.build()

    // Search 图标
    val Search = ImageVector.Builder(
        name = "search",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color(0xFF8E8E93)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(19f, 11f)
            arcToRelative(8f, 8f, 0f, true, true, -16f, 0f)
            arcToRelative(8f, 8f, 0f, true, true, 16f, 0f)
            close()
            moveTo(21f, 21f)
            lineTo(16.65f, 16.65f)
        }
    }.build()

    // Memory 图标
    val Memory = ImageVector.Builder(
        name = "memory",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF374151)),
            stroke = null,
            pathFillType = PathFillType.NonZero
        ) {
            // Main pencil/edit icon
            moveTo(17.253f, 11.852f)
            curveTo(18.34f, 10.966f, 19.944f, 11.03f, 20.957f, 12.043f)
            curveTo(21.97f, 13.056f, 22.034f, 14.66f, 21.147f, 15.747f)
            lineTo(20.957f, 15.957f)
            lineTo(16.387f, 20.527f)
            curveTo(15.871f, 21.043f, 15.226f, 21.406f, 14.521f, 21.581f)
            lineTo(14.217f, 21.645f)
            lineTo(12.166f, 21.986f)
            curveTo(11.848f, 22.039f, 11.523f, 21.935f, 11.295f, 21.707f)
            curveTo(11.067f, 21.479f, 10.963f, 21.154f, 11.016f, 20.836f)
            lineTo(11.356f, 18.786f)
            lineTo(11.419f, 18.48f)
            curveTo(11.593f, 17.775f, 11.957f, 17.129f, 12.474f, 16.612f)
            lineTo(17.043f, 12.043f)
            lineTo(17.253f, 11.852f)
            close()

            moveTo(19.543f, 13.457f)
            curveTo(19.262f, 13.176f, 18.817f, 13.158f, 18.516f, 13.404f)
            lineTo(18.457f, 13.457f)
            lineTo(13.888f, 18.026f)
            curveTo(13.63f, 18.284f, 13.448f, 18.608f, 13.36f, 18.96f)
            lineTo(13.329f, 19.113f)
            lineTo(13.217f, 19.783f)
            lineTo(13.888f, 19.672f)
            lineTo(14.04f, 19.641f)
            curveTo(14.392f, 19.553f, 14.715f, 19.371f, 14.973f, 19.113f)
            lineTo(19.543f, 14.543f)
            lineTo(19.596f, 14.484f)
            curveTo(19.841f, 14.183f, 19.824f, 13.738f, 19.543f, 13.457f)
            close()

            moveTo(18f, 8.5f)
            verticalLineTo(4.8f)
            curveTo(18f, 4.634f, 17.866f, 4.5f, 17.7f, 4.5f)
            lineTo(11f, 4.5f)
            verticalLineTo(14f)
            curveTo(11f, 14.552f, 10.552f, 15f, 10f, 15f)
            curveTo(9.448f, 15f, 9f, 14.552f, 9f, 14f)
            verticalLineTo(4.5f)
            lineTo(8f, 4.5f)
            curveTo(6.895f, 4.5f, 6f, 5.395f, 6f, 6.5f)
            verticalLineTo(17.5f)
            curveTo(6f, 18.605f, 6.895f, 19.5f, 8f, 19.5f)
            curveTo(8.552f, 19.5f, 9f, 19.948f, 9f, 20.5f)
            curveTo(9f, 21.052f, 8.552f, 21.5f, 8f, 21.5f)
            curveTo(5.791f, 21.5f, 4f, 19.709f, 4f, 17.5f)
            verticalLineTo(6.5f)
            curveTo(4f, 4.291f, 5.791f, 2.5f, 8f, 2.5f)
            lineTo(17.7f, 2.5f)
            curveTo(18.97f, 2.5f, 20f, 3.53f, 20f, 4.8f)
            verticalLineTo(8.5f)
            curveTo(20f, 9.052f, 19.552f, 9.5f, 19f, 9.5f)
            curveTo(18.448f, 9.5f, 18f, 9.052f, 18f, 8.5f)
            close()
        }
    }.build()

    // Trash/Delete 图标 - 使用用户提供的SVG
    val Trash = ImageVector.Builder(
        name = "trash",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 1024f,
        viewportHeight = 1024f
    ).apply {
        path(
            fill = SolidColor(Color(0xFFFFFFFF)),
            stroke = null,
            pathFillType = PathFillType.NonZero
        ) {
            // 垃圾桶图标路径
            moveTo(202.666667f, 256f)
            lineTo(160f, 256f)
            arcTo(32f, 32f, 0f, false, true, 160f, 192f)
            lineTo(864f, 192f)
            arcTo(32f, 32f, 0f, false, true, 864f, 256f)
            lineTo(266.666667f, 256f)
            lineTo(266.666667f, 821.333333f)
            arcTo(53.333333f, 53.333333f, 0f, false, false, 320f, 874.666667f)
            lineTo(704f, 874.666667f)
            arcTo(53.333333f, 53.333333f, 0f, false, false, 757.333333f, 821.333333f)
            lineTo(757.333333f, 352f)
            arcTo(32f, 32f, 0f, false, true, 821.333333f, 352f)
            lineTo(821.333333f, 821.333333f)
            curveTo(821.333333f, 886.133333f, 768.8f, 938.666667f, 704f, 938.666667f)
            lineTo(320f, 938.666667f)
            curveTo(255.2f, 938.666667f, 202.666667f, 886.133333f, 202.666667f, 821.333333f)
            lineTo(202.666667f, 256f)
            close()
            moveTo(426.666667f, 149.333333f)
            arcTo(32f, 32f, 0f, false, true, 426.666667f, 85.333333f)
            lineTo(597.333333f, 85.333333f)
            arcTo(32f, 32f, 0f, false, true, 597.333333f, 149.333333f)
            lineTo(426.666667f, 149.333333f)
            close()
            moveTo(394.666667f, 437.333333f)
            arcTo(32f, 32f, 0f, false, true, 458.666667f, 437.333333f)
            lineTo(458.666667f, 693.333333f)
            arcTo(32f, 32f, 0f, false, true, 394.666667f, 693.333333f)
            lineTo(394.666667f, 437.333333f)
            close()
            moveTo(565.333333f, 437.333333f)
            arcTo(32f, 32f, 0f, false, true, 629.333333f, 437.333333f)
            lineTo(629.333333f, 693.333333f)
            arcTo(32f, 32f, 0f, false, true, 565.333333f, 693.333333f)
            lineTo(565.333333f, 437.333333f)
            close()
        }
    }.build()

    // Back 箭头图标
    val Back = ImageVector.Builder(
        name = "back",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color(0xFF1C1C1E)),
            strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(19f, 12f)
            horizontalLineTo(5f)
            moveTo(12f, 19f)
            lineTo(5f, 12f)
            lineTo(12f, 5f)
        }
    }.build()

    // Plus/Add 图标
    val Plus = ImageVector.Builder(
        name = "plus",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color(0xFF1C1C1E)),
            strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 5f)
            verticalLineTo(19f)
            moveTo(5f, 12f)
            horizontalLineTo(19f)
        }
    }.build()

    // Check/Save 图标
    val Check = ImageVector.Builder(
        name = "check",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color(0xFF1C1C1E)),
            strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(20f, 6f)
            lineTo(9f, 17f)
            lineTo(4f, 12f)
        }
    }.build()

    // Chevron Right 图标
    val ChevronRight = ImageVector.Builder(
        name = "chevron_right",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color(0xFF8E8E93)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(9f, 18f)
            lineTo(15f, 12f)
            lineTo(9f, 6f)
        }
    }.build()

    // User/Profile 图标
    val User = ImageVector.Builder(
        name = "user",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color(0xFF666666)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(20f, 21f)
            verticalLineToRelative(-2f)
            arcTo(4f, 4f, 0f, false, false, 16f, 15f)
            horizontalLineTo(8f)
            arcTo(4f, 4f, 0f, false, false, 4f, 17f)
            verticalLineTo(21f)
            moveTo(12f, 7f)
            arcTo(4f, 4f, 0f, true, true, 0f, 0.01f)
            close()
        }
    }.build()

    // Accent Color 图标
    val Accent = ImageVector.Builder(
        name = "accent",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 20f,
        viewportHeight = 20f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF1C1C1E)),
            stroke = null
        ) {
            moveTo(13.886f, 2.713f)
            curveTo(14.834f, 1.924f, 16.247f, 1.973f, 17.139f, 2.864f)
            lineTo(17.306f, 3.049f)
            curveTo(18.031f, 3.94f, 18.026f, 5.227f, 17.294f, 6.113f)
            lineTo(17.126f, 6.296f)
            lineTo(14.711f, 8.675f)
            lineTo(14.865f, 8.829f)
            lineTo(15.043f, 9.029f)
            curveTo(15.812f, 9.985f, 15.696f, 11.337f, 14.945f, 12.234f)
            lineTo(14.775f, 12.418f)
            curveTo(13.688f, 13.49f, 12.839f, 14.662f, 12.18f, 16.005f)
            lineTo(11.908f, 16.591f)
            curveTo(11.376f, 17.814f, 9.9f, 18.587f, 8.588f, 17.927f)
            lineTo(8.462f, 17.859f)
            curveTo(7.405f, 17.246f, 6.647f, 16.801f, 5.981f, 16.288f)
            curveTo(5.388f, 15.831f, 4.881f, 15.33f, 4.287f, 14.625f)
            lineTo(4.027f, 14.311f)
            curveTo(3.852f, 14.095f, 3.83f, 13.793f, 3.971f, 13.554f)
            lineTo(4.353f, 12.905f)
            lineTo(3.733f, 13.12f)
            curveTo(3.483f, 13.208f, 3.209f, 13.137f, 3.031f, 12.949f)
            lineTo(2.962f, 12.863f)
            curveTo(2.822f, 12.653f, 2.683f, 12.439f, 2.547f, 12.22f)
            lineTo(2.142f, 11.545f)
            curveTo(1.362f, 10.203f, 2.147f, 8.648f, 3.41f, 8.1f)
            lineTo(3.997f, 7.829f)
            curveTo(5.342f, 7.171f, 6.515f, 6.322f, 7.588f, 5.236f)
            lineTo(7.772f, 5.066f)
            curveTo(8.734f, 4.263f, 10.218f, 4.187f, 11.179f, 5.147f)
            lineTo(11.332f, 5.3f)
            lineTo(13.703f, 2.882f)
            lineTo(13.886f, 2.713f)
            close()
            moveTo(6.897f, 7.604f)
            curveTo(6.084f, 8.217f, 5.213f, 8.738f, 4.266f, 9.175f)
            lineTo(3.94f, 9.321f)
            curveTo(3.241f, 9.624f, 2.998f, 10.371f, 3.292f, 10.877f)
            lineTo(3.677f, 11.517f)
            curveTo(3.714f, 11.576f, 3.752f, 11.633f, 3.789f, 11.692f)
            lineTo(5.627f, 11.052f)
            lineTo(5.727f, 11.026f)
            curveTo(5.962f, 10.983f, 6.206f, 11.07f, 6.36f, 11.259f)
            curveTo(6.537f, 11.475f, 6.56f, 11.778f, 6.419f, 12.018f)
            lineTo(5.355f, 13.823f)
            curveTo(5.883f, 14.444f, 6.31f, 14.861f, 6.794f, 15.235f)
            curveTo(7.377f, 15.684f, 8.055f, 16.086f, 9.129f, 16.708f)
            lineTo(9.227f, 16.757f)
            curveTo(9.729f, 16.971f, 10.404f, 16.715f, 10.689f, 16.061f)
            lineTo(10.835f, 15.736f)
            curveTo(11.272f, 14.79f, 11.792f, 13.918f, 12.405f, 13.106f)
            lineTo(6.897f, 7.604f)
            close()
            moveTo(16.198f, 3.805f)
            curveTo(15.797f, 3.404f, 15.162f, 3.382f, 14.735f, 3.737f)
            lineTo(14.652f, 3.813f)
            lineTo(11.811f, 6.71f)
            curveTo(11.687f, 6.837f, 11.518f, 6.909f, 11.341f, 6.91f)
            curveTo(11.163f, 6.911f, 10.993f, 6.84f, 10.867f, 6.715f)
            lineTo(10.238f, 6.087f)
            curveTo(9.833f, 5.682f, 9.13f, 5.664f, 8.631f, 6.081f)
            lineTo(8.534f, 6.171f)
            curveTo(8.335f, 6.372f, 8.132f, 6.564f, 7.926f, 6.751f)
            lineTo(13.258f, 12.078f)
            curveTo(13.445f, 11.872f, 13.64f, 11.671f, 13.842f, 11.472f)
            lineTo(13.931f, 11.374f)
            curveTo(14.32f, 10.909f, 14.331f, 10.267f, 14.001f, 9.855f)
            lineTo(13.925f, 9.771f)
            lineTo(13.296f, 9.143f)
            curveTo(13.17f, 9.017f, 13.101f, 8.846f, 13.102f, 8.669f)
            curveTo(13.102f, 8.492f, 13.174f, 8.323f, 13.3f, 8.198f)
            lineTo(16.192f, 5.349f)
            lineTo(16.268f, 5.266f)
            curveTo(16.597f, 4.868f, 16.6f, 4.289f, 16.274f, 3.888f)
            lineTo(16.198f, 3.805f)
            close()
        }
    }.build()

    // Personalization 图标 - 笑脸（圆圈 + 嘴角上扬弧线）
    val Personalization = ImageVector.Builder(
        name = "personalization",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF0D0D0D)),
            stroke = null,
            pathFillType = PathFillType.EvenOdd
        ) {
            // 外圆环（填充规则 EvenOdd 自动挖空）
            moveTo(12f, 4f)
            curveTo(7.582f, 4f, 4f, 7.582f, 4f, 12f)
            curveTo(4f, 16.418f, 7.582f, 20f, 12f, 20f)
            curveTo(16.418f, 20f, 20f, 16.418f, 20f, 12f)
            curveTo(20f, 7.582f, 16.418f, 4f, 12f, 4f)
            close()
            moveTo(2f, 12f)
            curveTo(2f, 6.477f, 6.477f, 2f, 12f, 2f)
            curveTo(17.523f, 2f, 22f, 6.477f, 22f, 12f)
            curveTo(22f, 17.523f, 17.523f, 22f, 12f, 22f)
            curveTo(6.477f, 22f, 2f, 17.523f, 2f, 12f)
            close()
            // 笑脸嘴巴弧线（实心路径，利用 EvenOdd 在圆圈内显示）
            moveTo(7.399f, 14.201f)
            curveTo(7.841f, 13.869f, 8.468f, 13.958f, 8.799f, 14.4f)
            curveTo(10.37f, 16.49f, 13.63f, 16.49f, 15.2f, 14.4f)
            curveTo(15.532f, 13.958f, 16.159f, 13.869f, 16.601f, 14.201f)
            curveTo(17.042f, 14.533f, 17.131f, 15.16f, 16.799f, 15.601f)
            curveTo(14.429f, 18.757f, 9.571f, 18.757f, 7.201f, 15.601f)
            curveTo(6.869f, 15.16f, 6.958f, 14.533f, 7.399f, 14.201f)
            close()
        }
    }.build()

    // Appearance 图标
    val Appearance = ImageVector.Builder(
        name = "appearance",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF1C1C1E)),
            stroke = null
        ) {
            // 外观图标 - 半个圆
            moveTo(12f, 2f)
            curveTo(17.523f, 2f, 22f, 6.477f, 22f, 12f)
            curveTo(22f, 17.523f, 17.523f, 22f, 12f, 22f)
            curveTo(6.477f, 22f, 2f, 17.523f, 2f, 12f)
            curveTo(2f, 6.477f, 6.477f, 2f, 12f, 2f)
            close()
            moveTo(13f, 3.667f)
            verticalLineTo(20.333f)
            curveTo(16f, 20f, 18.5f, 18.833f, 20f, 16.667f)
            curveTo(21.5f, 14.5f, 21.667f, 12f, 21.667f, 12f)
            curveTo(21.667f, 12f, 21.5f, 9.5f, 20f, 7.333f)
            curveTo(18.5f, 5.167f, 16f, 4f, 13f, 3.667f)
            close()
        }
    }.build()

    // Language 图标
    val Language = ImageVector.Builder(
        name = "language",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // 外圆
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color(0xFF1C1C1E)),
            strokeLineWidth = 1.5f
        ) {
            moveTo(12f, 2f)
            arcToRelative(10f, 10f, 0f, true, false, 0f, 20f)
            arcToRelative(10f, 10f, 0f, true, false, 0f, -20f)
            close()
        }
        // 垂直椭圆
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color(0xFF1C1C1E)),
            strokeLineWidth = 1.5f
        ) {
            moveTo(12f, 2f)
            arcToRelative(4f, 10f, 0f, true, false, 0f, 20f)
            arcToRelative(4f, 10f, 0f, true, false, 0f, -20f)
            close()
        }
        // 水平线
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color(0xFF1C1C1E)),
            strokeLineWidth = 1.5f
        ) {
            moveTo(2f, 12f)
            horizontalLineTo(22f)
        }
    }.build()

    // Notifications 图标
    val Notifications = ImageVector.Builder(
        name = "notifications",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF1C1C1E)),
            stroke = null
        ) {
            moveTo(3.801f, 9.603f)
            curveTo(4.257f, 5.419f, 7.791f, 2.25f, 12f, 2.25f)
            curveTo(16.209f, 2.25f, 19.743f, 5.419f, 20.199f, 9.603f)
            lineTo(20.873f, 15.783f)
            curveTo(21.002f, 16.966f, 20.075f, 18f, 18.885f, 18f)
            horizontalLineTo(16.842f)
            curveTo(16.287f, 20.156f, 14.33f, 21.75f, 12f, 21.75f)
            curveTo(9.67f, 21.75f, 7.713f, 20.156f, 7.157f, 18f)
            horizontalLineTo(5.115f)
            curveTo(3.925f, 18f, 2.998f, 16.966f, 3.127f, 15.783f)
            lineTo(3.801f, 9.603f)
            close()
            moveTo(9.272f, 18f)
            curveTo(9.746f, 19.033f, 10.789f, 19.75f, 12f, 19.75f)
            curveTo(13.211f, 19.75f, 14.254f, 19.033f, 14.728f, 18f)
            horizontalLineTo(9.272f)
            close()
            moveTo(12f, 4.25f)
            curveTo(8.812f, 4.25f, 6.135f, 6.651f, 5.789f, 9.82f)
            lineTo(5.115f, 16f)
            horizontalLineTo(18.885f)
            lineTo(18.211f, 9.82f)
            curveTo(17.865f, 6.651f, 15.188f, 4.25f, 12f, 4.25f)
            close()
        }
    }.build()

    // Model 图标 - 与 HTML icons.css 一致
    val Model = ImageVector.Builder(
        name = "model",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF000000)),
            stroke = null,
            pathFillType = PathFillType.NonZero
        ) {
            addPathNodes(
                "M491,621q70,0 119,-45t49,-109q0,-57 -36.5,-96.5T534,331q-47,0 -79.5,30T422,435q0,19 7.5,37t21.5,33l57,-57q-3,-2 -4.5,-5t-1.5,-7q0,-11 9,-17.5t23,-6.5q20,0 33,16.5t13,39.5q0,31 -25.5,52.5T492,542q-47,0 -79.5,-38T380,411q0,-29 11,-55.5t31,-46.5l-57,-57q-32,31 -49,72t-17,86q0,88 56,149.5T491,621ZM240,880v-172q-57,-52 -88.5,-121.5T120,440q0,-150 105,-255t255,-105q125,0 221.5,73.5T827,345l52,205q5,19 -7,34.5T840,600h-80v120q0,33 -23.5,56.5T680,800h-80v80h-80v-160h160v-200h108l-38,-155q-23,-91 -98,-148t-172,-57q-116,0 -198,81t-82,197q0,60 24.5,114t69.5,96l26,24v208h-80ZM494,520Z"
            )
        }
    }.build()

    // Model Services 图标 - 与 HTML icons.css 一致
    val ModelServices = ImageVector.Builder(
        name = "model_services",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF1F1F1F)),
            stroke = null,
            pathFillType = PathFillType.NonZero
        ) {
            addPathNodes(
                "M260,800q-91,0 -155.5,-63T40,583q0,-78 47,-139t123,-78q25,-92 100,-149t170,-57q117,0 198.5,81.5T760,440q69,8 114.5,59.5T920,620q0,75 -52.5,127.5T740,800L260,800ZM260,720h480q42,0 71,-29t29,-71q0,-42 -29,-71t-71,-29h-60v-80q0,-83 -58.5,-141.5T480,240q-83,0 -141.5,58.5T280,440h-20q-58,0 -99,41t-41,99q0,58 41,99t99,41ZM480,480Z"
            )
        }
    }.build()

    // App Developer 图标（原 MCP 代码图标）
    val AppDeveloper = ImageVector.Builder(
        name = "app_developer",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF1C1C1E)),
            stroke = null
        ) {
            moveTo(14.447f, 7.106f)
            arcToRelative(1f, 1f, 0f, false, true, 0.447f, 1.341f)
            lineToRelative(-4f, 8f)
            arcToRelative(1f, 1f, 0f, true, true, -1.788f, -0.894f)
            lineToRelative(4f, -8f)
            arcToRelative(1f, 1f, 0f, false, true, 1.341f, -0.447f)
            close()
            moveTo(6.6f, 7.2f)
            arcToRelative(1f, 1f, 0f, false, true, 0.2f, 1.4f)
            lineTo(4.25f, 12f)
            lineToRelative(2.55f, 3.4f)
            arcToRelative(1f, 1f, 0f, false, true, -1.6f, 1.2f)
            lineToRelative(-3f, -4f)
            arcToRelative(1f, 1f, 0f, false, true, 0f, -1.2f)
            lineToRelative(3f, -4f)
            arcToRelative(1f, 1f, 0f, false, true, 1.4f, -0.2f)
            close()
            moveTo(17.4f, 7.2f)
            arcToRelative(1f, 1f, 0f, false, true, 1.4f, 0.2f)
            lineToRelative(3f, 4f)
            arcToRelative(1f, 1f, 0f, false, true, 0f, 1.2f)
            lineToRelative(-3f, 4f)
            arcToRelative(1f, 1f, 0f, false, true, -1.6f, -1.2f)
            lineToRelative(2.55f, -3.4f)
            lineToRelative(-2.55f, -3.4f)
            arcToRelative(1f, 1f, 0f, false, true, 0.2f, -1.4f)
            close()
        }
    }.build()

    // MCP Tools 图标 - 来自 SVG (四个工具图标)
    val MCPTools = ImageVector.Builder(
        name = "mcp_tools",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF141413)),
            stroke = null,
            pathFillType = PathFillType.EvenOdd
        ) {
            // Circle (magnifier) at bottom right
            moveTo(17.75f, 13.5f)
            curveTo(20.097f, 13.5f, 22f, 15.403f, 22f, 17.75f)
            curveTo(22f, 20.097f, 20.097f, 22f, 17.75f, 22f)
            curveTo(15.403f, 22f, 13.5f, 20.097f, 13.5f, 17.75f)
            curveTo(13.5f, 15.403f, 15.403f, 13.5f, 17.75f, 13.5f)
            close()
            moveTo(17.75f, 15f)
            curveTo(16.231f, 15f, 15f, 16.231f, 15f, 17.75f)
            curveTo(15f, 19.268f, 16.232f, 20.5f, 17.75f, 20.5f)
            curveTo(19.269f, 20.5f, 20.5f, 19.268f, 20.5f, 17.75f)
            curveTo(20.5f, 16.231f, 19.269f, 15f, 17.75f, 15f)
            close()
            // Gear/star at bottom left
            moveTo(8.372f, 13.602f)
            curveTo(8.604f, 13.467f, 8.891f, 13.466f, 9.124f, 13.6f)
            curveTo(9.357f, 13.733f, 9.5f, 13.982f, 9.5f, 14.25f)
            lineTo(9.5f, 16.152f)
            lineTo(11.128f, 17.103f)
            curveTo(11.358f, 17.237f, 11.5f, 17.483f, 11.5f, 17.75f)
            curveTo(11.5f, 18.016f, 11.358f, 18.263f, 11.128f, 18.397f)
            lineTo(9.5f, 19.346f)
            lineTo(9.5f, 21.25f)
            curveTo(9.5f, 21.518f, 9.356f, 21.767f, 9.123f, 21.9f)
            curveTo(8.89f, 22.034f, 8.604f, 22.033f, 8.372f, 21.897f)
            lineTo(6.75f, 20.95f)
            lineTo(5.128f, 21.897f)
            curveTo(4.896f, 22.032f, 4.61f, 22.034f, 4.377f, 21.9f)
            curveTo(4.144f, 21.767f, 4f, 21.518f, 4f, 21.25f)
            lineTo(4f, 19.346f)
            lineTo(2.372f, 18.397f)
            curveTo(2.142f, 18.263f, 2f, 18.017f, 2f, 17.75f)
            curveTo(2f, 17.483f, 2.142f, 17.237f, 2.372f, 17.103f)
            lineTo(4f, 16.152f)
            lineTo(4f, 14.25f)
            curveTo(4f, 13.982f, 4.144f, 13.733f, 4.377f, 13.6f)
            curveTo(4.609f, 13.466f, 4.896f, 13.467f, 5.128f, 13.602f)
            lineTo(6.75f, 14.548f)
            lineTo(8.372f, 13.602f)
            close()
            // Inner gear detail
            moveTo(7.128f, 16.063f)
            curveTo(6.895f, 16.199f, 6.605f, 16.199f, 6.372f, 16.063f)
            lineTo(5.5f, 15.555f)
            lineTo(5.5f, 16.583f)
            curveTo(5.5f, 16.85f, 5.358f, 17.096f, 5.128f, 17.23f)
            lineTo(4.237f, 17.749f)
            lineTo(5.128f, 18.269f)
            curveTo(5.358f, 18.403f, 5.5f, 18.649f, 5.5f, 18.916f)
            lineTo(5.5f, 19.944f)
            lineTo(6.372f, 19.435f)
            lineTo(6.462f, 19.391f)
            curveTo(6.677f, 19.301f, 6.924f, 19.316f, 7.128f, 19.435f)
            lineTo(8f, 19.944f)
            lineTo(8f, 18.916f)
            curveTo(8f, 18.649f, 8.142f, 18.403f, 8.372f, 18.269f)
            lineTo(9.262f, 17.749f)
            lineTo(8.372f, 17.23f)
            curveTo(8.142f, 17.096f, 8f, 16.85f, 8f, 16.583f)
            lineTo(8f, 15.555f)
            lineTo(7.128f, 16.063f)
            close()
            // Diamond at top left
            moveTo(6.183f, 2.183f)
            curveTo(6.476f, 1.89f, 6.95f, 1.89f, 7.243f, 2.183f)
            lineTo(11.212f, 6.151f)
            curveTo(11.352f, 6.292f, 11.432f, 6.484f, 11.432f, 6.683f)
            curveTo(11.431f, 6.881f, 11.352f, 7.072f, 11.212f, 7.213f)
            lineTo(7.243f, 11.182f)
            curveTo(6.95f, 11.474f, 6.475f, 11.474f, 6.183f, 11.182f)
            lineTo(2.213f, 7.213f)
            curveTo(2.072f, 7.072f, 1.993f, 6.881f, 1.993f, 6.683f)
            curveTo(1.993f, 6.484f, 2.073f, 6.292f, 2.213f, 6.151f)
            lineTo(6.183f, 2.183f)
            close()
            // Inner diamond detail
            moveTo(3.804f, 6.683f)
            lineTo(6.713f, 9.591f)
            lineTo(9.621f, 6.683f)
            lineTo(6.713f, 3.773f)
            lineTo(3.804f, 6.683f)
            close()
            // Funnel at top right
            moveTo(21.014f, 2f)
            curveTo(21.786f, 2f, 22.267f, 2.839f, 21.877f, 3.506f)
            lineTo(20.269f, 6.25f)
            lineTo(21.877f, 8.994f)
            curveTo(22.267f, 9.661f, 21.786f, 10.5f, 21.014f, 10.5f)
            lineTo(14.941f, 10.5f)
            curveTo(14.165f, 10.5f, 13.685f, 9.652f, 14.084f, 8.985f)
            lineTo(15.726f, 6.25f)
            lineTo(14.084f, 3.515f)
            curveTo(13.684f, 2.848f, 14.164f, 2f, 14.941f, 2f)
            lineTo(21.014f, 2f)
            close()
            // Inner funnel detail
            moveTo(17.166f, 5.735f)
            curveTo(17.356f, 6.052f, 17.356f, 6.448f, 17.166f, 6.765f)
            lineTo(15.824f, 9f)
            lineTo(20.142f, 9f)
            lineTo(18.827f, 6.756f)
            curveTo(18.644f, 6.444f, 18.645f, 6.056f, 18.827f, 5.744f)
            lineTo(20.142f, 3.5f)
            lineTo(15.824f, 3.5f)
            lineTo(17.166f, 5.735f)
            close()
        }
    }.build()

    // Camera 图标
    val Camera = ImageVector.Builder(
        name = "camera",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF000000)),
            stroke = null
        ) {
            moveTo(12f, 4f)
            arcToRelative(3f, 3f, 0f, false, false, -2.6f, 1.5f)
            arcToRelative(1f, 1f, 0f, false, true, -0.865f, 0.5f)
            horizontalLineTo(5f)
            arcToRelative(1f, 1f, 0f, false, false, -1f, 1f)
            verticalLineToRelative(11f)
            arcToRelative(1f, 1f, 0f, false, false, 1f, 1f)
            horizontalLineToRelative(14f)
            arcToRelative(1f, 1f, 0f, false, false, 1f, -1f)
            verticalLineTo(7f)
            arcToRelative(1f, 1f, 0f, false, false, -1f, -1f)
            horizontalLineToRelative(-3.535f)
            arcToRelative(1f, 1f, 0f, false, true, -0.866f, -0.5f)
            arcToRelative(2.998f, 2.998f, 0f, false, false, -2.599f, -1.5f)
            close()
            moveTo(8f, 4f)
            arcToRelative(4.993f, 4.993f, 0f, false, true, 4f, -2f)
            arcToRelative(4.99f, 4.99f, 0f, false, true, 4f, 2f)
            horizontalLineToRelative(3f)
            arcToRelative(3f, 3f, 0f, false, true, 3f, 3f)
            verticalLineToRelative(11f)
            arcToRelative(3f, 3f, 0f, false, true, -3f, 3f)
            horizontalLineTo(5f)
            arcToRelative(3f, 3f, 0f, false, true, -3f, -3f)
            verticalLineTo(7f)
            arcToRelative(3f, 3f, 0f, false, true, 3f, -3f)
            horizontalLineToRelative(3f)
            close()
            moveTo(12f, 10f)
            arcToRelative(2f, 2f, 0f, true, false, 0f, 4f)
            arcToRelative(2f, 2f, 0f, true, false, 0f, -4f)
            close()
            moveTo(8f, 12f)
            arcToRelative(4f, 4f, 0f, true, true, 8f, 0f)
            arcToRelative(4f, 4f, 0f, true, true, -8f, 0f)
            close()
        }
    }.build()

    // Files/Attachment 图标
    val Files = ImageVector.Builder(
        name = "files",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF000000)),
            stroke = null
        ) {
            moveTo(9f, 7f)
            arcToRelative(5f, 5f, 0f, false, true, 10f, 0f)
            verticalLineToRelative(8f)
            arcToRelative(7f, 7f, 0f, true, true, -14f, 0f)
            verticalLineTo(9f)
            arcToRelative(1f, 1f, 0f, false, true, 2f, 0f)
            verticalLineToRelative(6f)
            arcToRelative(5f, 5f, 0f, false, false, 10f, 0f)
            verticalLineTo(7f)
            arcToRelative(3f, 3f, 0f, true, false, -6f, 0f)
            verticalLineTo(8f)
            arcToRelative(1f, 1f, 0f, true, false, 2f, 0f)
            verticalLineTo(9f)
            arcToRelative(1f, 1f, 0f, true, true, 2f, 0f)
            verticalLineTo(6f)
            arcToRelative(3f, 3f, 0f, true, true, -6f, 0f)
            verticalLineTo(7f)
            close()
        }
    }.build()

    // Globe/Web Search 图标
    val Globe = ImageVector.Builder(
        name = "globe",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF000000)),
            stroke = null
        ) {
            moveTo(12f, 2f)
            curveTo(17.523f, 2f, 22f, 6.477f, 22f, 12f)
            curveTo(22f, 17.523f, 17.523f, 22f, 12f, 22f)
            curveTo(6.477f, 22f, 2f, 17.523f, 2f, 12f)
            curveTo(2f, 6.477f, 6.477f, 2f, 12f, 2f)
            close()
            moveTo(9.771f, 13f)
            curveTo(9.854f, 14.99f, 10.179f, 16.742f, 10.643f, 18.024f)
            curveTo(10.914f, 18.775f, 11.213f, 19.313f, 11.495f, 19.644f)
            curveTo(11.781f, 19.978f, 11.955f, 20f, 12f, 20f)
            curveTo(12.045f, 20f, 12.219f, 19.978f, 12.505f, 19.644f)
            curveTo(12.787f, 19.313f, 13.086f, 18.775f, 13.357f, 18.024f)
            curveTo(13.821f, 16.742f, 14.146f, 14.99f, 14.229f, 13f)
            horizontalLineTo(9.771f)
            close()
            moveTo(4.064f, 13f)
            curveTo(4.431f, 15.941f, 6.394f, 18.385f, 9.06f, 19.44f)
            curveTo(8.953f, 19.204f, 8.854f, 18.958f, 8.762f, 18.703f)
            curveTo(8.208f, 17.171f, 7.853f, 15.181f, 7.77f, 13f)
            horizontalLineTo(4.064f)
            close()
            moveTo(16.23f, 13f)
            curveTo(16.147f, 15.181f, 15.792f, 17.171f, 15.238f, 18.703f)
            curveTo(15.146f, 18.958f, 15.046f, 19.204f, 14.939f, 19.44f)
            curveTo(17.606f, 18.385f, 19.569f, 15.942f, 19.935f, 13f)
            horizontalLineTo(16.23f)
            close()
            moveTo(14.939f, 4.56f)
            curveTo(15.046f, 4.796f, 15.146f, 5.042f, 15.238f, 5.297f)
            curveTo(15.792f, 6.829f, 16.147f, 8.819f, 16.23f, 11f)
            horizontalLineTo(19.935f)
            curveTo(19.569f, 8.058f, 17.606f, 5.614f, 14.939f, 4.56f)
            close()
            moveTo(12f, 4f)
            curveTo(11.955f, 4f, 11.781f, 4.022f, 11.495f, 4.356f)
            curveTo(11.213f, 4.687f, 10.914f, 5.225f, 10.643f, 5.976f)
            curveTo(10.179f, 7.258f, 9.854f, 9.01f, 9.771f, 11f)
            horizontalLineTo(14.229f)
            curveTo(14.146f, 9.01f, 13.821f, 7.258f, 13.357f, 5.976f)
            curveTo(13.086f, 5.225f, 12.787f, 4.687f, 12.505f, 4.356f)
            curveTo(12.219f, 4.022f, 12.045f, 4f, 12f, 4f)
            close()
            moveTo(9.06f, 4.56f)
            curveTo(6.394f, 5.614f, 4.431f, 8.058f, 4.064f, 11f)
            horizontalLineTo(7.77f)
            curveTo(7.853f, 8.819f, 8.208f, 6.829f, 8.762f, 5.297f)
            curveTo(8.854f, 5.042f, 8.953f, 4.795f, 9.06f, 4.56f)
            close()
        }
    }.build()

    // AutoBrowser 指南针图标（对齐 Lucide compass）
    val AutoBrowserCompass = ImageVector.Builder(
        name = "autobrowser_compass",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color(0xFF1C1C1E)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 2f)
            arcToRelative(10f, 10f, 0f, true, true, 0f, 20f)
            arcToRelative(10f, 10f, 0f, true, true, 0f, -20f)
            close()
        }
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color(0xFF1C1C1E)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(16.24f, 7.76f)
            lineTo(14.436f, 13.171f)
            curveToRelative(-0.2f, 0.6f, -0.665f, 1.065f, -1.265f, 1.265f)
            lineTo(7.76f, 16.24f)
            lineTo(9.564f, 10.829f)
            curveToRelative(0.2f, -0.6f, 0.665f, -1.065f, 1.265f, -1.265f)
            lineTo(16.24f, 7.76f)
            close()
        }
    }.build()

    // Create Image 图标
    val CreateImage = ImageVector.Builder(
        name = "create_image",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF262626)),
            stroke = null
        ) {
            moveTo(18.438f, 2.513f)
            curveTo(19.331f, 2.051f, 20.388f, 2.259f, 21.064f, 2.936f)
            curveTo(21.741f, 3.612f, 21.949f, 4.669f, 21.487f, 5.562f)
            curveTo(20.628f, 7.218f, 19.611f, 8.669f, 18.387f, 9.973f)
            curveTo(19.618f, 11.504f, 20.543f, 13.089f, 20.934f, 14.582f)
            curveTo(21.389f, 16.321f, 21.133f, 18.074f, 19.625f, 19.281f)
            curveTo(18.413f, 20.25f, 17.052f, 20.119f, 15.964f, 19.67f)
            curveTo(14.906f, 19.233f, 13.967f, 18.451f, 13.323f, 17.829f)
            curveTo(12.926f, 17.445f, 12.915f, 16.812f, 13.298f, 16.415f)
            curveTo(13.682f, 16.017f, 14.315f, 16.006f, 14.712f, 16.39f)
            curveTo(15.293f, 16.951f, 16.016f, 17.528f, 16.728f, 17.822f)
            curveTo(17.411f, 18.104f, 17.928f, 18.077f, 18.376f, 17.719f)
            curveTo(19.081f, 17.155f, 19.322f, 16.323f, 18.999f, 15.088f)
            curveTo(18.714f, 14f, 18.005f, 12.717f, 16.938f, 11.365f)
            curveTo(16.358f, 11.87f, 15.738f, 12.353f, 15.075f, 12.818f)
            curveTo(15.045f, 12.839f, 15.015f, 12.858f, 14.984f, 12.875f)
            curveTo(14.922f, 13.602f, 14.677f, 14.225f, 14.247f, 14.721f)
            curveTo(13.756f, 15.285f, 13.11f, 15.588f, 12.492f, 15.757f)
            curveTo(11.507f, 16.025f, 10.328f, 16.008f, 9.467f, 15.995f)
            curveTo(9.302f, 15.993f, 9.148f, 15.991f, 9.009f, 15.991f)
            curveTo(8.457f, 15.991f, 8.009f, 15.543f, 8.009f, 14.991f)
            curveTo(8.009f, 14.852f, 8.007f, 14.698f, 8.005f, 14.533f)
            curveTo(7.992f, 13.672f, 7.975f, 12.493f, 8.244f, 11.508f)
            curveTo(8.412f, 10.89f, 8.715f, 10.244f, 9.279f, 9.753f)
            curveTo(9.775f, 9.322f, 10.398f, 9.078f, 11.125f, 9.016f)
            curveTo(11.142f, 8.985f, 11.161f, 8.955f, 11.182f, 8.925f)
            curveTo(11.579f, 8.359f, 11.989f, 7.824f, 12.415f, 7.318f)
            curveTo(9.525f, 5.732f, 6.745f, 5.862f, 5.304f, 7.303f)
            curveTo(4.336f, 8.271f, 3.954f, 9.799f, 4.297f, 11.629f)
            curveTo(4.639f, 13.454f, 5.694f, 15.472f, 7.425f, 17.203f)
            curveTo(8.447f, 18.225f, 9.551f, 18.939f, 10.455f, 19.395f)
            curveTo(10.906f, 19.622f, 11.298f, 19.782f, 11.595f, 19.882f)
            curveTo(11.818f, 19.956f, 11.945f, 19.984f, 11.992f, 19.994f)
            curveTo(12.016f, 20f, 12.019f, 20f, 12f, 20f)
            curveTo(12.553f, 20f, 13f, 20.448f, 13f, 21f)
            curveTo(13f, 21.552f, 12.552f, 22f, 12f, 22f)
            curveTo(11.697f, 22f, 11.307f, 21.895f, 10.957f, 21.777f)
            curveTo(10.563f, 21.644f, 10.083f, 21.447f, 9.553f, 21.18f)
            curveTo(8.495f, 20.646f, 7.208f, 19.815f, 6.011f, 18.617f)
            curveTo(4.032f, 16.638f, 2.757f, 14.268f, 2.331f, 11.998f)
            curveTo(1.907f, 9.734f, 2.319f, 7.46f, 3.889f, 5.889f)
            curveTo(6.343f, 3.436f, 10.412f, 3.792f, 13.814f, 5.817f)
            curveTo(15.173f, 4.501f, 16.691f, 3.419f, 18.438f, 2.513f)
            close()
            moveTo(13.257f, 9.471f)
            curveTo(13.784f, 9.776f, 14.224f, 10.216f, 14.529f, 10.743f)
            curveTo(16.793f, 9.037f, 18.441f, 7.093f, 19.711f, 4.642f)
            curveTo(19.75f, 4.565f, 19.745f, 4.445f, 19.65f, 4.35f)
            curveTo(19.555f, 4.255f, 19.434f, 4.25f, 19.358f, 4.289f)
            curveTo(16.907f, 5.559f, 14.963f, 7.207f, 13.257f, 9.471f)
            close()
            moveTo(10.002f, 13.999f)
            curveTo(10.716f, 14.002f, 11.39f, 13.984f, 11.966f, 13.827f)
            curveTo(12.354f, 13.721f, 12.593f, 13.575f, 12.737f, 13.409f)
            curveTo(12.867f, 13.26f, 13f, 13.002f, 13f, 12.495f)
            curveTo(13f, 11.67f, 12.33f, 11f, 11.505f, 11f)
            curveTo(10.998f, 11f, 10.741f, 11.133f, 10.591f, 11.263f)
            curveTo(10.425f, 11.407f, 10.279f, 11.646f, 10.173f, 12.034f)
            curveTo(10.016f, 12.61f, 9.998f, 13.284f, 10.002f, 13.999f)
            close()
        }
    }.build()

    // Copy 图标
    val Copy = ImageVector.Builder(
        name = "copy",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF374151)),
            stroke = null
        ) {
            moveTo(12.759f, 2f)
            horizontalLineTo(16.241f)
            curveTo(17.046f, 2f, 17.711f, 2f, 18.252f, 2.044f)
            curveTo(18.814f, 2.09f, 19.331f, 2.189f, 19.816f, 2.436f)
            curveTo(20.569f, 2.819f, 21.181f, 3.431f, 21.564f, 4.184f)
            curveTo(21.811f, 4.669f, 21.91f, 5.186f, 21.956f, 5.748f)
            curveTo(22f, 6.289f, 22f, 6.954f, 22f, 7.759f)
            verticalLineTo(11.241f)
            curveTo(22f, 12.046f, 22f, 12.711f, 21.956f, 13.252f)
            curveTo(21.91f, 13.814f, 21.811f, 14.331f, 21.564f, 14.816f)
            curveTo(21.181f, 15.569f, 20.569f, 16.181f, 19.816f, 16.564f)
            curveTo(19.331f, 16.811f, 18.814f, 16.91f, 18.252f, 16.956f)
            curveTo(17.891f, 16.985f, 17.475f, 16.995f, 16.998f, 16.998f)
            curveTo(16.995f, 17.475f, 16.985f, 17.891f, 16.956f, 18.252f)
            curveTo(16.91f, 18.814f, 16.811f, 19.331f, 16.564f, 19.816f)
            curveTo(16.181f, 20.569f, 15.569f, 21.181f, 14.816f, 21.564f)
            curveTo(14.331f, 21.811f, 13.814f, 21.91f, 13.252f, 21.956f)
            curveTo(12.711f, 22f, 12.046f, 22f, 11.241f, 22f)
            horizontalLineTo(7.759f)
            curveTo(6.954f, 22f, 6.289f, 22f, 5.748f, 21.956f)
            curveTo(5.186f, 21.91f, 4.669f, 21.811f, 4.184f, 21.564f)
            curveTo(3.431f, 21.181f, 2.819f, 20.569f, 2.436f, 19.816f)
            curveTo(2.189f, 19.331f, 2.09f, 18.814f, 2.044f, 18.252f)
            curveTo(2f, 17.711f, 2f, 17.046f, 2f, 16.241f)
            verticalLineTo(12.759f)
            curveTo(2f, 11.954f, 2f, 11.289f, 2.044f, 10.748f)
            curveTo(2.09f, 10.186f, 2.189f, 9.669f, 2.436f, 9.184f)
            curveTo(2.819f, 8.431f, 3.431f, 7.819f, 4.184f, 7.436f)
            curveTo(4.669f, 7.189f, 5.186f, 7.09f, 5.748f, 7.044f)
            curveTo(6.109f, 7.015f, 6.525f, 7.005f, 7.002f, 7.002f)
            curveTo(7.005f, 6.525f, 7.015f, 6.109f, 7.044f, 5.748f)
            curveTo(7.09f, 5.186f, 7.189f, 4.669f, 7.436f, 4.184f)
            curveTo(7.819f, 3.431f, 8.431f, 2.819f, 9.184f, 2.436f)
            curveTo(9.669f, 2.189f, 10.186f, 2.09f, 10.748f, 2.044f)
            curveTo(11.289f, 2f, 11.954f, 2f, 12.759f, 2f)
            close()
            moveTo(9.002f, 7f)
            lineTo(11.241f, 7f)
            curveTo(12.046f, 7f, 12.711f, 7f, 13.252f, 7.044f)
            curveTo(13.814f, 7.09f, 14.331f, 7.189f, 14.816f, 7.436f)
            curveTo(15.569f, 7.819f, 16.181f, 8.431f, 16.564f, 9.184f)
            curveTo(16.811f, 9.669f, 16.91f, 10.186f, 16.956f, 10.748f)
            curveTo(17f, 11.289f, 17f, 11.954f, 17f, 12.759f)
            verticalLineTo(14.998f)
            curveTo(17.445f, 14.995f, 17.795f, 14.986f, 18.089f, 14.962f)
            curveTo(18.527f, 14.927f, 18.752f, 14.862f, 18.908f, 14.782f)
            curveTo(19.284f, 14.59f, 19.59f, 14.284f, 19.782f, 13.908f)
            curveTo(19.862f, 13.752f, 19.927f, 13.527f, 19.962f, 13.089f)
            curveTo(19.999f, 12.639f, 20f, 12.057f, 20f, 11.2f)
            verticalLineTo(7.8f)
            curveTo(20f, 6.943f, 19.999f, 6.361f, 19.962f, 5.911f)
            curveTo(19.927f, 5.473f, 19.862f, 5.248f, 19.782f, 5.092f)
            curveTo(19.59f, 4.716f, 19.284f, 4.41f, 18.908f, 4.218f)
            curveTo(18.752f, 4.138f, 18.527f, 4.073f, 18.089f, 4.038f)
            curveTo(17.639f, 4.001f, 17.057f, 4f, 16.2f, 4f)
            horizontalLineTo(12.8f)
            curveTo(11.943f, 4f, 11.361f, 4.001f, 10.911f, 4.038f)
            curveTo(10.473f, 4.073f, 10.248f, 4.138f, 10.092f, 4.218f)
            curveTo(9.716f, 4.41f, 9.41f, 4.716f, 9.218f, 5.092f)
            curveTo(9.138f, 5.248f, 9.073f, 5.473f, 9.038f, 5.911f)
            curveTo(9.014f, 6.205f, 9.005f, 6.554f, 9.002f, 7f)
            close()
            moveTo(5.911f, 9.038f)
            curveTo(5.473f, 9.073f, 5.248f, 9.138f, 5.092f, 9.218f)
            curveTo(4.716f, 9.41f, 4.41f, 9.716f, 4.218f, 10.092f)
            curveTo(4.138f, 10.248f, 4.073f, 10.473f, 4.038f, 10.911f)
            curveTo(4.001f, 11.361f, 4f, 11.943f, 4f, 12.8f)
            verticalLineTo(16.2f)
            curveTo(4f, 17.057f, 4.001f, 17.639f, 4.038f, 18.089f)
            curveTo(4.073f, 18.527f, 4.138f, 18.752f, 4.218f, 18.908f)
            curveTo(4.41f, 19.284f, 4.716f, 19.59f, 5.092f, 19.782f)
            curveTo(5.248f, 19.862f, 5.473f, 19.927f, 5.911f, 19.962f)
            curveTo(6.361f, 19.999f, 6.943f, 20f, 7.8f, 20f)
            horizontalLineTo(11.2f)
            curveTo(12.057f, 20f, 12.639f, 19.999f, 13.089f, 19.962f)
            curveTo(13.527f, 19.927f, 13.752f, 19.862f, 13.908f, 19.782f)
            curveTo(14.284f, 19.59f, 14.59f, 19.284f, 14.782f, 18.908f)
            curveTo(14.862f, 18.752f, 14.927f, 18.527f, 14.962f, 18.089f)
            curveTo(14.999f, 17.639f, 15f, 17.057f, 15f, 16.2f)
            verticalLineTo(12.8f)
            curveTo(15f, 11.943f, 14.999f, 11.361f, 14.962f, 10.911f)
            curveTo(14.927f, 10.473f, 14.862f, 10.248f, 14.782f, 10.092f)
            curveTo(14.59f, 9.716f, 14.284f, 9.41f, 13.908f, 9.218f)
            curveTo(13.752f, 9.138f, 13.527f, 9.073f, 13.089f, 9.038f)
            curveTo(12.639f, 9.001f, 12.057f, 9f, 11.2f, 9f)
            horizontalLineTo(7.8f)
            curveTo(6.943f, 9f, 6.361f, 9.001f, 5.911f, 9.038f)
            close()
        }
    }.build()

    // Edit/Pencil 图标 - 与 HTML 示例消息工具栏一致
    val Edit = ImageVector.Builder(
        name = "edit",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF374151)),
            stroke = null,
            pathFillType = PathFillType.NonZero
        ) {
            // Main pencil body
            moveTo(13.293f, 4.293f)
            curveTo(15.064f, 2.522f, 17.936f, 2.522f, 19.707f, 4.293f)
            curveTo(21.478f, 6.064f, 21.478f, 8.935f, 19.707f, 10.707f)
            lineTo(12.157f, 18.257f)
            curveTo(11.644f, 18.769f, 11.301f, 19.119f, 10.898f, 19.394f)
            curveTo(10.557f, 19.628f, 10.186f, 19.817f, 9.798f, 19.96f)
            curveTo(9.34f, 20.127f, 8.857f, 20.205f, 8.142f, 20.324f)
            lineTo(5.27f, 20.802f)
            curveTo(5.102f, 20.83f, 4.91f, 20.863f, 4.744f, 20.876f)
            curveTo(4.574f, 20.888f, 4.303f, 20.893f, 4.017f, 20.77f)
            curveTo(3.663f, 20.618f, 3.381f, 20.337f, 3.229f, 19.983f)
            curveTo(3.107f, 19.697f, 3.111f, 19.426f, 3.124f, 19.256f)
            curveTo(3.137f, 19.09f, 3.169f, 18.899f, 3.197f, 18.731f)
            lineTo(3.677f, 15.859f)
            curveTo(3.796f, 15.144f, 3.872f, 14.66f, 4.04f, 14.202f)
            curveTo(4.182f, 13.813f, 4.373f, 13.443f, 4.606f, 13.101f)
            curveTo(4.882f, 12.698f, 5.231f, 12.355f, 5.743f, 11.842f)
            lineTo(13.293f, 4.293f)
            close()

            // Inner cutout
            moveTo(7.157f, 13.257f)
            curveTo(6.593f, 13.821f, 6.404f, 14.017f, 6.258f, 14.23f)
            curveTo(6.118f, 14.435f, 6.003f, 14.657f, 5.918f, 14.89f)
            curveTo(5.829f, 15.133f, 5.78f, 15.401f, 5.649f, 16.187f)
            lineTo(5.217f, 18.783f)
            lineTo(7.813f, 18.351f)
            curveTo(8.6f, 18.22f, 8.868f, 18.171f, 9.11f, 18.082f)
            curveTo(9.343f, 17.996f, 9.565f, 17.883f, 9.771f, 17.743f)
            curveTo(9.984f, 17.597f, 10.179f, 17.407f, 10.743f, 16.842f)
            lineTo(16.586f, 11f)
            lineTo(13f, 7.414f)
            lineTo(7.157f, 13.257f)
            close()

            // Eraser end detail
            moveTo(18.293f, 5.707f)
            curveTo(17.303f, 4.717f, 15.697f, 4.717f, 14.707f, 5.707f)
            lineTo(14.414f, 6f)
            lineTo(18f, 9.586f)
            lineTo(18.293f, 9.293f)
            curveTo(19.283f, 8.302f, 19.283f, 6.697f, 18.293f, 5.707f)
            close()
        }
    }.build()

    // Volume/Speaker 图标
    val Volume = ImageVector.Builder(
        name = "volume",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF374151)),
            stroke = null
        ) {
            moveTo(11f, 4.91f)
            curveTo(11f, 4.475f, 10.483f, 4.247f, 10.162f, 4.541f)
            lineTo(7.501f, 6.98f)
            curveTo(6.786f, 7.636f, 5.85f, 8f, 4.88f, 8f)
            curveTo(3.842f, 8f, 3f, 8.842f, 3f, 9.88f)
            verticalLineTo(14.12f)
            curveTo(3f, 15.158f, 3.842f, 16f, 4.88f, 16f)
            curveTo(5.85f, 16f, 6.786f, 16.364f, 7.501f, 17.02f)
            lineTo(10.162f, 19.459f)
            curveTo(10.483f, 19.753f, 11f, 19.525f, 11f, 19.09f)
            verticalLineTo(4.91f)
            close()
            moveTo(8.811f, 3.067f)
            curveTo(10.414f, 1.597f, 13f, 2.735f, 13f, 4.91f)
            verticalLineTo(19.09f)
            curveTo(13f, 21.265f, 10.414f, 22.403f, 8.811f, 20.933f)
            lineTo(6.15f, 18.494f)
            curveTo(5.803f, 18.176f, 5.35f, 18f, 4.88f, 18f)
            curveTo(2.737f, 18f, 1f, 16.263f, 1f, 14.12f)
            verticalLineTo(9.88f)
            curveTo(1f, 7.737f, 2.737f, 6f, 4.88f, 6f)
            curveTo(5.35f, 6f, 5.803f, 5.824f, 6.15f, 5.506f)
            lineTo(8.811f, 3.067f)
            close()
            moveTo(20.317f, 6.357f)
            curveTo(20.802f, 6.093f, 21.409f, 6.273f, 21.673f, 6.758f)
            curveTo(22.52f, 8.318f, 23f, 10.104f, 23f, 12f)
            curveTo(23f, 13.851f, 22.542f, 15.597f, 21.733f, 17.13f)
            curveTo(21.475f, 17.618f, 20.87f, 17.805f, 20.382f, 17.547f)
            curveTo(19.893f, 17.289f, 19.706f, 16.684f, 19.964f, 16.196f)
            curveTo(20.625f, 14.944f, 21f, 13.517f, 21f, 12f)
            curveTo(21f, 10.446f, 20.607f, 8.986f, 19.915f, 7.713f)
            curveTo(19.652f, 7.227f, 19.832f, 6.62f, 20.317f, 6.357f)
            close()
            moveTo(15.8f, 7.9f)
            curveTo(16.241f, 7.569f, 16.868f, 7.658f, 17.2f, 8.099f)
            curveTo(18.016f, 9.186f, 18.5f, 10.538f, 18.5f, 12f)
            curveTo(18.5f, 13.313f, 18.11f, 14.537f, 17.439f, 15.56f)
            curveTo(17.136f, 16.022f, 16.516f, 16.151f, 16.054f, 15.848f)
            curveTo(15.592f, 15.545f, 15.464f, 14.925f, 15.766f, 14.464f)
            curveTo(16.23f, 13.756f, 16.5f, 12.911f, 16.5f, 12f)
            curveTo(16.5f, 10.986f, 16.166f, 10.052f, 15.601f, 9.301f)
            curveTo(15.269f, 8.859f, 15.358f, 8.232f, 15.8f, 7.9f)
            close()
        }
    }.build()

    // Share/Network 图标
    val Share = ImageVector.Builder(
        name = "share",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color(0xFF374151)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // 三个圆点
            moveTo(5.5f, 12f)
            arcToRelative(2.5f, 2.5f, 0f, true, true, 5f, 0f)
            arcToRelative(2.5f, 2.5f, 0f, true, true, -5f, 0f)
            moveTo(16.5f, 5.5f)
            arcToRelative(2.5f, 2.5f, 0f, true, true, 5f, 0f)
            arcToRelative(2.5f, 2.5f, 0f, true, true, -5f, 0f)
            moveTo(16.5f, 18.5f)
            arcToRelative(2.5f, 2.5f, 0f, true, true, 5f, 0f)
            arcToRelative(2.5f, 2.5f, 0f, true, true, -5f, 0f)
            // 连接线
            moveTo(14.5f, 6.583f)
            lineTo(7.5f, 10.796f)
            moveTo(14.5f, 17.417f)
            lineTo(7.5f, 13.204f)
        }
    }.build()

    // Refresh/Regenerate 图标
    val Refresh = ImageVector.Builder(
        name = "refresh",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF374151)),
            stroke = null
        ) {
            moveTo(3.07f, 10.876f)
            curveTo(3.623f, 6.436f, 7.41f, 3f, 12f, 3f)
            curveTo(14.282f, 3f, 16.403f, 3.851f, 18.012f, 5.254f)
            verticalLineTo(4f)
            curveTo(18.012f, 3.448f, 18.459f, 3f, 19.012f, 3f)
            curveTo(19.564f, 3f, 20.012f, 3.448f, 20.012f, 4f)
            verticalLineTo(8f)
            curveTo(20.012f, 8.552f, 19.564f, 9f, 19.012f, 9f)
            horizontalLineTo(15f)
            curveTo(14.448f, 9f, 14f, 8.552f, 14f, 8f)
            curveTo(14f, 7.448f, 14.448f, 7f, 15f, 7f)
            horizontalLineTo(16.957f)
            curveTo(15.676f, 5.764f, 13.91f, 5f, 12f, 5f)
            curveTo(8.431f, 5f, 5.485f, 7.672f, 5.054f, 11.124f)
            curveTo(4.986f, 11.672f, 4.486f, 12.061f, 3.938f, 11.992f)
            curveTo(3.39f, 11.924f, 3.001f, 11.424f, 3.07f, 10.876f)
            close()
            moveTo(20.062f, 12.008f)
            curveTo(20.61f, 12.076f, 20.999f, 12.576f, 20.93f, 13.124f)
            curveTo(20.377f, 17.564f, 16.59f, 21f, 12f, 21f)
            curveTo(9.723f, 21f, 7.608f, 20.153f, 6f, 18.756f)
            verticalLineTo(20f)
            curveTo(6f, 20.552f, 5.552f, 21f, 5f, 21f)
            curveTo(4.448f, 21f, 4f, 20.552f, 4f, 20f)
            verticalLineTo(16f)
            curveTo(4f, 15.448f, 4.448f, 15f, 5f, 15f)
            horizontalLineTo(9f)
            curveTo(9.552f, 15f, 10f, 15.448f, 10f, 16f)
            curveTo(10f, 16.552f, 9.552f, 17f, 9f, 17f)
            horizontalLineTo(7.043f)
            curveTo(8.324f, 18.236f, 10.09f, 19f, 12f, 19f)
            curveTo(15.569f, 19f, 18.515f, 16.328f, 18.946f, 12.876f)
            curveTo(19.014f, 12.328f, 19.514f, 11.939f, 20.062f, 12.008f)
            close()
        }
    }.build()

    // More/Dots 图标
    val More = ImageVector.Builder(
        name = "more",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF374151)),
            stroke = SolidColor(Color(0xFF374151)),
            strokeLineWidth = 2f
        ) {
            // Top dot
            moveTo(12f, 5f)
            arcToRelative(1f, 1f, 0f, true, true, 0f, 0.01f)
            close()
            // Middle dot
            moveTo(12f, 12f)
            arcToRelative(1f, 1f, 0f, true, true, 0f, 0.01f)
            close()
            // Bottom dot
            moveTo(12f, 19f)
            arcToRelative(1f, 1f, 0f, true, true, 0f, 0.01f)
            close()
        }
    }.build()

    // Text Only 图标
    val TextOnly = ImageVector.Builder(
        name = "text_only",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4f, 6f)
            horizontalLineTo(20f)
            moveTo(4f, 12f)
            horizontalLineTo(20f)
            moveTo(4f, 18f)
            horizontalLineTo(16f)
        }
    }.build()

    // Text & Image 图标
    val TextImage = ImageVector.Builder(
        name = "text_image",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3f, 3f)
            horizontalLineTo(21f)
            verticalLineTo(21f)
            horizontalLineTo(3f)
            close()
            moveTo(8.5f, 8.5f)
            arcToRelative(1.5f, 1.5f, 0f, true, true, 0f, 0.01f)
            close()
            moveTo(21f, 15f)
            lineTo(16f, 10f)
            lineTo(5f, 21f)
        }
    }.build()

    // Close/X 图标
    val Close = ImageVector.Builder(
        name = "close",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color(0xFF8E8E93)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(18f, 6f)
            lineTo(6f, 18f)
            moveTo(6f, 6f)
            lineTo(18f, 18f)
        }
    }.build()

    // Send/Arrow Up 图标
    val Send = ImageVector.Builder(
        name = "send",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 19f)
            verticalLineTo(5f)
            moveTo(5f, 12f)
            lineTo(12f, 5f)
            lineTo(19f, 12f)
        }
    }.build()

    // Think/Thinking 图标 - 大脑/思考图标
    val Think = ImageVector.Builder(
        name = "think",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF8E8E93)),
            stroke = null
        ) {
            // 灯泡形状
            moveTo(12f, 2f)
            curveTo(8.134f, 2f, 5f, 5.134f, 5f, 9f)
            curveTo(5f, 11.422f, 6.154f, 13.576f, 8f, 14.915f)
            verticalLineTo(17f)
            curveTo(8f, 17.552f, 8.448f, 18f, 9f, 18f)
            horizontalLineTo(10f)
            verticalLineTo(19f)
            curveTo(10f, 19.552f, 10.448f, 20f, 11f, 20f)
            horizontalLineTo(13f)
            curveTo(13.552f, 20f, 14f, 19.552f, 14f, 19f)
            verticalLineTo(18f)
            horizontalLineTo(15f)
            curveTo(15.552f, 18f, 16f, 17.552f, 16f, 17f)
            verticalLineTo(14.915f)
            curveTo(17.846f, 13.576f, 19f, 11.422f, 19f, 9f)
            curveTo(19f, 5.134f, 15.866f, 2f, 12f, 2f)
            close()
            moveTo(12f, 4f)
            curveTo(14.761f, 4f, 17f, 6.239f, 17f, 9f)
            curveTo(17f, 10.797f, 16.062f, 12.373f, 14.583f, 13.238f)
            curveTo(14.237f, 13.439f, 14f, 13.804f, 14f, 14.207f)
            verticalLineTo(16f)
            horizontalLineTo(10f)
            verticalLineTo(14.207f)
            curveTo(10f, 13.804f, 9.763f, 13.439f, 9.417f, 13.238f)
            curveTo(7.938f, 12.373f, 7f, 10.797f, 7f, 9f)
            curveTo(7f, 6.239f, 9.239f, 4f, 12f, 4f)
            close()
            // 灯泡内的光线/思考线条
            moveTo(12f, 6.5f)
            curveTo(11.172f, 6.5f, 10.5f, 7.172f, 10.5f, 8f)
            curveTo(10.5f, 8.828f, 11.172f, 9.5f, 12f, 9.5f)
            curveTo(12.828f, 9.5f, 13.5f, 8.828f, 13.5f, 8f)
            curveTo(13.5f, 7.172f, 12.828f, 6.5f, 12f, 6.5f)
            close()
        }
    }.build()

    // Monitor/Desktop 图标 - System appearance (使用太阳图标)
    val Monitor = ImageVector.Builder(
        name = "monitor",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF1C1C1E)),
            stroke = null
        ) {
            // 太阳图标路径
            moveTo(12f, 1f)
            curveTo(12.552f, 1f, 13f, 1.448f, 13f, 2f)
            verticalLineTo(4f)
            curveTo(13f, 4.552f, 12.552f, 5f, 12f, 5f)
            curveTo(11.448f, 5f, 11f, 4.552f, 11f, 4f)
            verticalLineTo(2f)
            curveTo(11f, 1.448f, 11.448f, 1f, 12f, 1f)
            close()
            moveTo(4.222f, 4.222f)
            curveTo(4.611f, 3.833f, 5.239f, 3.833f, 5.636f, 4.222f)
            lineTo(7.05f, 5.636f)
            curveTo(7.439f, 6.025f, 7.439f, 6.653f, 7.05f, 7.05f)
            curveTo(6.661f, 7.439f, 6.025f, 7.439f, 5.636f, 7.05f)
            lineTo(4.222f, 5.636f)
            curveTo(3.833f, 5.247f, 3.833f, 4.611f, 4.222f, 4.222f)
            close()
            moveTo(19.778f, 4.222f)
            curveTo(20.167f, 4.611f, 20.167f, 5.247f, 19.778f, 5.636f)
            lineTo(18.364f, 7.05f)
            curveTo(17.975f, 7.439f, 17.339f, 7.439f, 16.95f, 7.05f)
            curveTo(16.561f, 6.661f, 16.561f, 6.025f, 16.95f, 5.636f)
            lineTo(18.364f, 4.222f)
            curveTo(18.753f, 3.833f, 19.389f, 3.833f, 19.778f, 4.222f)
            close()
            moveTo(12f, 9f)
            curveTo(13.657f, 9f, 15f, 10.343f, 15f, 12f)
            curveTo(15f, 13.657f, 13.657f, 15f, 12f, 15f)
            curveTo(10.343f, 15f, 9f, 13.657f, 9f, 12f)
            curveTo(9f, 10.343f, 10.343f, 9f, 12f, 9f)
            close()
            moveTo(7f, 12f)
            curveTo(7f, 9.239f, 9.239f, 7f, 12f, 7f)
            curveTo(14.761f, 7f, 17f, 9.239f, 17f, 12f)
            curveTo(17f, 14.761f, 14.761f, 17f, 12f, 17f)
            curveTo(9.239f, 17f, 7f, 14.761f, 7f, 12f)
            close()
            moveTo(1f, 12f)
            curveTo(1f, 11.448f, 1.448f, 11f, 2f, 11f)
            horizontalLineTo(4f)
            curveTo(4.552f, 11f, 5f, 11.448f, 5f, 12f)
            curveTo(5f, 12.552f, 4.552f, 13f, 4f, 13f)
            horizontalLineTo(2f)
            curveTo(1.448f, 13f, 1f, 12.552f, 1f, 12f)
            close()
            moveTo(19f, 12f)
            curveTo(19f, 11.448f, 19.448f, 11f, 20f, 11f)
            horizontalLineTo(22f)
            curveTo(22.552f, 11f, 23f, 11.448f, 23f, 12f)
            curveTo(23f, 12.552f, 22.552f, 13f, 22f, 13f)
            horizontalLineTo(20f)
            curveTo(19.448f, 13f, 19f, 12.552f, 19f, 12f)
            close()
            moveTo(7.05f, 16.95f)
            curveTo(7.439f, 17.339f, 7.439f, 17.975f, 7.05f, 18.364f)
            lineTo(5.636f, 19.778f)
            curveTo(5.247f, 20.167f, 4.611f, 20.167f, 4.222f, 19.778f)
            curveTo(3.833f, 19.389f, 3.833f, 18.753f, 4.222f, 18.364f)
            lineTo(5.636f, 16.95f)
            curveTo(6.025f, 16.561f, 6.661f, 16.561f, 7.05f, 16.95f)
            close()
            moveTo(16.95f, 16.95f)
            curveTo(17.339f, 16.561f, 17.975f, 16.561f, 18.364f, 16.95f)
            lineTo(19.778f, 18.364f)
            curveTo(20.167f, 18.753f, 20.167f, 19.389f, 19.778f, 19.778f)
            curveTo(19.389f, 20.167f, 18.753f, 20.167f, 18.364f, 19.778f)
            lineTo(16.95f, 18.364f)
            curveTo(16.561f, 17.975f, 16.561f, 17.339f, 16.95f, 16.95f)
            close()
            moveTo(12f, 19f)
            curveTo(12.552f, 19f, 13f, 19.448f, 13f, 20f)
            verticalLineTo(22f)
            curveTo(13f, 22.552f, 12.552f, 23f, 12f, 23f)
            curveTo(11.448f, 23f, 11f, 22.552f, 11f, 22f)
            verticalLineTo(20f)
            curveTo(11f, 19.448f, 11.448f, 19f, 12f, 19f)
            close()
        }
    }.build()

     // Sun 图标 - Light appearance (Lucide Sun, fillType=evenodd)
     val Sun = ImageVector.Builder(
         name = "sun",
         defaultWidth = 24.dp,
         defaultHeight = 24.dp,
         viewportWidth = 24f,
         viewportHeight = 24f
     ).apply {
         path(
             fill = SolidColor(Color(0xFF1C1C1E)),
             fillAlpha = 1f,
             stroke = null,
             pathFillType = PathFillType.EvenOdd
         ) {
             // 顶部射线 (12,1) → (12,5)
             moveTo(12f, 1f)
             arcToRelative(1f, 1f, 0f, false, true, 1f, 1f)
             verticalLineToRelative(2f)
             arcToRelative(1f, 1f, 0f, true, true, -2f, 0f)
             verticalLineTo(2f)
             arcToRelative(1f, 1f, 0f, false, true, 1f, -1f)
             close()
             // 左上射线
             moveTo(4.222f, 4.222f)
             arcToRelative(1f, 1f, 0f, false, true, 1.414f, 0f)
             lineTo(7.05f, 5.636f)
             arcToRelative(1f, 1f, 0f, true, true, -1.414f, 1.414f)
             lineTo(4.222f, 5.636f)
             arcToRelative(1f, 1f, 0f, false, true, 0f, -1.414f)
             close()
             // 右上射线
             moveTo(19.778f, 4.222f)
             arcToRelative(1f, 1f, 0f, false, true, 0f, 1.414f)
             lineTo(18.364f, 7.05f)
             arcToRelative(1f, 1f, 0f, false, true, -1.414f, -1.414f)
             lineToRelative(1.414f, -1.414f)
             arcToRelative(1f, 1f, 0f, false, true, 1.414f, 0f)
             close()
             // 中心圆 (内圈 r=3, evenodd 挖空) + 外圈 r=5
             moveTo(12f, 9f)
             arcToRelative(3f, 3f, 0f, true, false, 0f, 6f)
             arcToRelative(3f, 3f, 0f, false, false, 0f, -6f)
             close()
             moveTo(7f, 12f)
             arcToRelative(5f, 5f, 0f, true, true, 10f, 0f)
             arcToRelative(5f, 5f, 0f, false, true, -10f, 0f)
             close()
             // 左射线
             moveTo(1f, 12f)
             arcToRelative(1f, 1f, 0f, false, true, 1f, -1f)
             horizontalLineToRelative(2f)
             arcToRelative(1f, 1f, 0f, true, true, 0f, 2f)
             horizontalLineTo(2f)
             arcToRelative(1f, 1f, 0f, false, true, -1f, -1f)
             close()
             // 右射线
             moveTo(19f, 12f)
             arcToRelative(1f, 1f, 0f, false, true, 1f, -1f)
             horizontalLineToRelative(2f)
             arcToRelative(1f, 1f, 0f, true, true, 0f, 2f)
             horizontalLineToRelative(-2f)
             arcToRelative(1f, 1f, 0f, false, true, -1f, -1f)
             close()
             // 左下射线
             moveTo(7.05f, 16.95f)
             arcToRelative(1f, 1f, 0f, false, true, 0f, 1.414f)
             lineToRelative(-1.414f, 1.414f)
             arcToRelative(1f, 1f, 0f, false, true, -1.414f, -1.414f)
             lineToRelative(1.414f, -1.414f)
             arcToRelative(1f, 1f, 0f, false, true, 1.414f, 0f)
             close()
             // 右下射线
             moveTo(16.95f, 16.95f)
             arcToRelative(1f, 1f, 0f, false, true, 1.414f, 0f)
             lineToRelative(1.414f, 1.414f)
             arcToRelative(1f, 1f, 0f, false, true, -1.414f, 1.414f)
             lineToRelative(-1.414f, -1.414f)
             arcToRelative(1f, 1f, 0f, false, true, 0f, -1.414f)
             close()
             // 底部射线
             moveTo(12f, 19f)
             arcToRelative(1f, 1f, 0f, false, true, 1f, 1f)
             verticalLineToRelative(2f)
             arcToRelative(1f, 1f, 0f, true, true, -2f, 0f)
             verticalLineToRelative(-2f)
             arcToRelative(1f, 1f, 0f, false, true, 1f, -1f)
             close()
         }
     }.build()

    // Moon 图标 - Dark appearance (使用太阳图标)
    val Moon = ImageVector.Builder(
        name = "moon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF1C1C1E)),
            stroke = null
        ) {
            // 太阳图标路径
            moveTo(12f, 1f)
            curveTo(12.552f, 1f, 13f, 1.448f, 13f, 2f)
            verticalLineTo(4f)
            curveTo(13f, 4.552f, 12.552f, 5f, 12f, 5f)
            curveTo(11.448f, 5f, 11f, 4.552f, 11f, 4f)
            verticalLineTo(2f)
            curveTo(11f, 1.448f, 11.448f, 1f, 12f, 1f)
            close()
            moveTo(4.222f, 4.222f)
            curveTo(4.611f, 3.833f, 5.239f, 3.833f, 5.636f, 4.222f)
            lineTo(7.05f, 5.636f)
            curveTo(7.439f, 6.025f, 7.439f, 6.653f, 7.05f, 7.05f)
            curveTo(6.661f, 7.439f, 6.025f, 7.439f, 5.636f, 7.05f)
            lineTo(4.222f, 5.636f)
            curveTo(3.833f, 5.247f, 3.833f, 4.611f, 4.222f, 4.222f)
            close()
            moveTo(19.778f, 4.222f)
            curveTo(20.167f, 4.611f, 20.167f, 5.247f, 19.778f, 5.636f)
            lineTo(18.364f, 7.05f)
            curveTo(17.975f, 7.439f, 17.339f, 7.439f, 16.95f, 7.05f)
            curveTo(16.561f, 6.661f, 16.561f, 6.025f, 16.95f, 5.636f)
            lineTo(18.364f, 4.222f)
            curveTo(18.753f, 3.833f, 19.389f, 3.833f, 19.778f, 4.222f)
            close()
            moveTo(12f, 9f)
            curveTo(13.657f, 9f, 15f, 10.343f, 15f, 12f)
            curveTo(15f, 13.657f, 13.657f, 15f, 12f, 15f)
            curveTo(10.343f, 15f, 9f, 13.657f, 9f, 12f)
            curveTo(9f, 10.343f, 10.343f, 9f, 12f, 9f)
            close()
            moveTo(7f, 12f)
            curveTo(7f, 9.239f, 9.239f, 7f, 12f, 7f)
            curveTo(14.761f, 7f, 17f, 9.239f, 17f, 12f)
            curveTo(17f, 14.761f, 14.761f, 17f, 12f, 17f)
            curveTo(9.239f, 17f, 7f, 14.761f, 7f, 12f)
            close()
            moveTo(1f, 12f)
            curveTo(1f, 11.448f, 1.448f, 11f, 2f, 11f)
            horizontalLineTo(4f)
            curveTo(4.552f, 11f, 5f, 11.448f, 5f, 12f)
            curveTo(5f, 12.552f, 4.552f, 13f, 4f, 13f)
            horizontalLineTo(2f)
            curveTo(1.448f, 13f, 1f, 12.552f, 1f, 12f)
            close()
            moveTo(19f, 12f)
            curveTo(19f, 11.448f, 19.448f, 11f, 20f, 11f)
            horizontalLineTo(22f)
            curveTo(22.552f, 11f, 23f, 11.448f, 23f, 12f)
            curveTo(23f, 12.552f, 22.552f, 13f, 22f, 13f)
            horizontalLineTo(20f)
            curveTo(19.448f, 13f, 19f, 12.552f, 19f, 12f)
            close()
            moveTo(7.05f, 16.95f)
            curveTo(7.439f, 17.339f, 7.439f, 17.975f, 7.05f, 18.364f)
            lineTo(5.636f, 19.778f)
            curveTo(5.247f, 20.167f, 4.611f, 20.167f, 4.222f, 19.778f)
            curveTo(3.833f, 19.389f, 3.833f, 18.753f, 4.222f, 18.364f)
            lineTo(5.636f, 16.95f)
            curveTo(6.025f, 16.561f, 6.661f, 16.561f, 7.05f, 16.95f)
            close()
            moveTo(16.95f, 16.95f)
            curveTo(17.339f, 16.561f, 17.975f, 16.561f, 18.364f, 16.95f)
            lineTo(19.778f, 18.364f)
            curveTo(20.167f, 18.753f, 20.167f, 19.389f, 19.778f, 19.778f)
            curveTo(19.389f, 20.167f, 18.753f, 20.167f, 18.364f, 19.778f)
            lineTo(16.95f, 18.364f)
            curveTo(16.561f, 17.975f, 16.561f, 17.339f, 16.95f, 16.95f)
            close()
            moveTo(12f, 19f)
            curveTo(12.552f, 19f, 13f, 19.448f, 13f, 20f)
            verticalLineTo(22f)
            curveTo(13f, 22.552f, 12.552f, 23f, 12f, 23f)
            curveTo(11.448f, 23f, 11f, 22.552f, 11f, 22f)
            verticalLineTo(20f)
            curveTo(11f, 19.448f, 11.448f, 19f, 12f, 19f)
            close()
        }
    }.build()

    // Image 图标 - 用于相册导入
    val Image = ImageVector.Builder(
        name = "image",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color(0xFF1C1C1E)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // 矩形框
            moveTo(3f, 3f)
            lineTo(21f, 3f)
            lineTo(21f, 21f)
            lineTo(3f, 21f)
            close()
            // 小圆点
            moveTo(8.5f, 8.5f)
            arcTo(1.5f, 1.5f, 0f, true, true, 0.01f, 0f)
            close()
            // 斜线
            moveTo(21f, 15f)
            lineTo(16f, 10f)
            lineTo(5f, 21f)
        }
    }.build()

    // OAuth 图标 - 用于 OAuth 连接按钮
    val OAuth = ImageVector.Builder(
        name = "oauth",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // 箭头
            moveTo(15f, 14f)
            lineTo(13f, 12f)
            lineTo(11f, 14f)
            moveTo(15f, 10f)
            verticalLineTo(7f)
            arcTo(2f, 2f, 0f, false, false, 13f, 5f)
            horizontalLineTo(6f)
            arcTo(2f, 2f, 0f, false, false, 4f, 7f)
            verticalLineTo(17f)
            arcTo(2f, 2f, 0f, false, false, 6f, 19f)
            horizontalLineTo(13f)
            arcTo(2f, 2f, 0f, false, false, 15f, 17f)
            verticalLineTo(14f)
            // 点
            moveTo(18f, 12f)
            horizontalLineTo(18.01f)
            moveTo(21f, 12f)
            horizontalLineTo(21.01f)
            // 箭头
            moveTo(21f, 12f)
            arcTo(9f, 9f, 0f, true, true, 12f, 3f)
            curveTo(14.52f, 3f, 16.93f, 4f, 18.74f, 5.74f)
            lineTo(21f, 8f)
            moveTo(21f, 3f)
            verticalLineTo(8f)
            horizontalLineTo(16f)
        }
    }.build()

    // Stream/SSE 图标
    val Stream = ImageVector.Builder(
        name = "stream",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color(0xFF1C1C1E)),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4f, 22f)
            horizontalLineTo(18f)
            arcTo(2f, 2f, 0f, false, false, 20f, 20f)
            verticalLineTo(7.5f)
            lineTo(14.5f, 2f)
            horizontalLineTo(6f)
            arcTo(2f, 2f, 0f, false, false, 4f, 4f)
            verticalLineTo(4f)
            moveTo(14f, 2f)
            verticalLineTo(6f)
            horizontalLineTo(20f)
            moveTo(2f, 15f)
            horizontalLineTo(12f)
            moveTo(5f, 12f)
            verticalLineTo(18f)
            moveTo(9f, 12f)
            verticalLineTo(18f)
        }
    }.build()

    // Tool 工具图标
    val Tool = ImageVector.Builder(
        name = "tool",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF8E8E93)),
            stroke = null,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(14.7f, 6.3f)
            arcTo(1f, 1f, 0f, false, false, 14.7f, 7.7f)
            lineTo(16.3f, 9.3f)
            arcTo(1f, 1f, 0f, false, false, 17.7f, 9.3f)
            lineTo(21.47f, 5.53f)
            arcTo(6f, 6f, 0f, false, true, 13.53f, 13.47f)
            lineTo(6.62f, 20.38f)
            arcTo(2.12f, 2.12f, 0f, false, true, 3.62f, 17.38f)
            lineTo(10.53f, 10.47f)
            arcTo(6f, 6f, 0f, false, true, 18.47f, 2.53f)
            lineTo(14.71f, 6.29f)
            close()
        }
    }.build()

    // Info/About 图标
    val Info = ImageVector.Builder(
        name = "info",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF141413)),
            stroke = null,
            pathFillType = PathFillType.NonZero
        ) {
            // "i" dot
            moveTo(440f, 680f)
            horizontalLineTo(520f)
            verticalLineTo(440f)
            horizontalLineTo(440f)
            verticalLineTo(680f)
            close()
            // "i" circle
            moveTo(480f, 360f)
            quadTo(497f, 360f, 508.5f, 348.5f)
            quadTo(520f, 337f, 520f, 320f)
            quadTo(520f, 303f, 508.5f, 291.5f)
            quadTo(497f, 280f, 480f, 280f)
            quadTo(463f, 280f, 451.5f, 291.5f)
            quadTo(440f, 303f, 440f, 320f)
            quadTo(440f, 337f, 451.5f, 348.5f)
            quadTo(463f, 360f, 480f, 360f)
            close()
            // outer circle
            moveTo(480f, 880f)
            quadTo(397f, 880f, 324f, 848.5f)
            quadTo(251f, 817f, 197f, 763f)
            quadTo(143f, 709f, 111.5f, 636f)
            quadTo(80f, 563f, 80f, 480f)
            quadTo(80f, 397f, 111.5f, 324f)
            quadTo(143f, 251f, 197f, 197f)
            quadTo(251f, 143f, 324f, 111.5f)
            quadTo(397f, 80f, 480f, 80f)
            quadTo(563f, 80f, 636f, 111.5f)
            quadTo(709f, 143f, 763f, 197f)
            quadTo(817f, 251f, 848.5f, 324f)
            quadTo(880f, 397f, 880f, 480f)
            quadTo(880f, 563f, 848.5f, 636f)
            quadTo(817f, 709f, 763f, 763f)
            quadTo(709f, 817f, 636f, 848.5f)
            quadTo(563f, 880f, 480f, 880f)
            close()
            // inner circle
            moveTo(480f, 800f)
            quadTo(614f, 800f, 707f, 707f)
            quadTo(800f, 614f, 800f, 480f)
            quadTo(800f, 346f, 707f, 253f)
            quadTo(614f, 160f, 480f, 160f)
            quadTo(346f, 160f, 253f, 253f)
            quadTo(160f, 346f, 160f, 480f)
            quadTo(160f, 614f, 253f, 707f)
            quadTo(346f, 800f, 480f, 800f)
            close()
        }
    }.build()

    // GitHub 图标
    val GitHub = ImageVector.Builder(
        name = "github",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF1C1C1E)),
            stroke = null,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(12f, 2f)
            curveTo(6.477f, 2f, 2f, 6.477f, 2f, 12f)
            curveTo(2f, 16.419f, 4.865f, 20.167f, 8.84f, 21.49f)
            curveTo(9.34f, 21.581f, 9.52f, 21.272f, 9.52f, 21.007f)
            curveTo(9.52f, 20.769f, 9.511f, 20.087f, 9.507f, 19.333f)
            curveTo(6.727f, 19.913f, 6.14f, 17.967f, 6.14f, 17.967f)
            curveTo(5.686f, 16.82f, 4.97f, 16.513f, 4.97f, 16.513f)
            curveTo(4.004f, 15.893f, 5.042f, 15.907f, 5.042f, 15.907f)
            curveTo(6.113f, 15.98f, 6.725f, 16.993f, 6.725f, 16.993f)
            curveTo(7.67f, 18.6f, 9.212f, 18.133f, 9.54f, 17.88f)
            curveTo(9.587f, 17.293f, 9.81f, 16.887f, 10.057f, 16.647f)
            curveTo(7.87f, 16.405f, 5.575f, 15.593f, 5.575f, 11.964f)
            curveTo(5.575f, 10.905f, 5.954f, 10.041f, 6.745f, 9.36f)
            curveTo(6.603f, 9.007f, 6.163f, 7.849f, 6.807f, 6.378f)
            curveTo(6.807f, 6.378f, 7.697f, 6.109f, 9.483f, 7.397f)
            curveTo(10.258f, 7.175f, 11.088f, 7.064f, 11.912f, 7.06f)
            curveTo(12.736f, 7.064f, 13.566f, 7.175f, 14.343f, 7.397f)
            curveTo(16.127f, 6.11f, 17.015f, 6.378f, 17.015f, 6.378f)
            curveTo(17.661f, 7.849f, 17.221f, 9.007f, 17.079f, 9.36f)
            curveTo(17.871f, 10.041f, 18.247f, 10.905f, 18.247f, 11.964f)
            curveTo(18.247f, 15.603f, 15.948f, 16.4f, 13.755f, 16.637f)
            curveTo(14.067f, 16.934f, 14.347f, 17.518f, 14.347f, 18.413f)
            curveTo(14.347f, 19.71f, 14.335f, 20.758f, 14.335f, 21.093f)
            curveTo(14.335f, 21.354f, 14.513f, 21.665f, 15.021f, 21.567f)
            curveTo(18.991f, 20.141f, 21.863f, 16.395f, 21.999f, 12f)
            curveTo(21.999f, 12f, 22f, 12f, 22f, 12f)
            curveTo(22f, 6.477f, 17.523f, 2f, 12f, 2f)
            close()
        }
    }.build()

    // QQ 图标
    val QQ = ImageVector.Builder(
        name = "qq",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF000000)),
            stroke = null,
            pathFillType = PathFillType.EvenOdd
        ) {
            // Simplified QQ penguin shape
            moveTo(12f, 2f)
            curveTo(7.588f, 2f, 5.152f, 5.605f, 5.152f, 10.16f)
            curveTo(5.152f, 10.227f, 5.152f, 10.294f, 5.154f, 10.363f)
            lineTo(4.601f, 11.793f)
            curveTo(4.36f, 12.44f, 4.173f, 12.982f, 4.033f, 13.448f)
            curveTo(3.688f, 14.598f, 3.514f, 15.575f, 3.501f, 16.36f)
            curveTo(3.494f, 16.767f, 3.531f, 17.136f, 3.619f, 17.468f)
            curveTo(3.685f, 17.714f, 3.78f, 17.948f, 3.929f, 18.176f)
            curveTo(4.205f, 18.603f, 4.613f, 18.952f, 5.206f, 19.086f)
            curveTo(5.076f, 19.64f, 5.151f, 20.227f, 5.446f, 20.741f)
            curveTo(5.819f, 21.393f, 6.279f, 21.633f, 6.884f, 21.798f)
            curveTo(7.124f, 21.863f, 7.382f, 21.906f, 7.678f, 21.936f)
            curveTo(8.118f, 21.981f, 8.664f, 22.001f, 9.291f, 22f)
            curveTo(10.216f, 21.998f, 11.309f, 21.949f, 12.001f, 21.884f)
            curveTo(12.693f, 21.949f, 13.786f, 21.998f, 14.711f, 22f)
            curveTo(15.338f, 22.001f, 15.884f, 21.981f, 16.324f, 21.936f)
            curveTo(16.62f, 21.906f, 16.878f, 21.863f, 17.118f, 21.798f)
            curveTo(17.723f, 21.633f, 18.183f, 21.393f, 18.556f, 20.741f)
            curveTo(18.851f, 20.227f, 18.926f, 19.64f, 18.796f, 19.086f)
            curveTo(19.389f, 18.952f, 19.797f, 18.603f, 20.073f, 18.176f)
            curveTo(20.222f, 17.948f, 20.317f, 17.714f, 20.383f, 17.468f)
            curveTo(20.471f, 17.136f, 20.508f, 16.767f, 20.501f, 16.36f)
            curveTo(20.488f, 15.575f, 20.314f, 14.598f, 19.969f, 13.448f)
            curveTo(19.826f, 12.975f, 19.638f, 12.428f, 19.393f, 11.822f)
            lineTo(18.847f, 10.363f)
            curveTo(18.849f, 10.294f, 18.849f, 10.227f, 18.849f, 10.16f)
            curveTo(18.849f, 5.605f, 16.413f, 2f, 12.001f, 2f)
            close()
            moveTo(12f, 4f)
            curveTo(15.446f, 4f, 16.849f, 7.088f, 16.849f, 10.16f)
            curveTo(16.849f, 10.343f, 16.839f, 10.697f, 16.839f, 10.718f)
            lineTo(17.535f, 12.514f)
            curveTo(17.726f, 13.029f, 17.911f, 13.56f, 18.051f, 14.023f)
            curveTo(18.712f, 16.211f, 18.496f, 17.119f, 18.334f, 17.139f)
            curveTo(17.986f, 17.182f, 16.979f, 15.491f, 16.979f, 15.491f)
            curveTo(16.979f, 16.47f, 16.492f, 17.749f, 15.437f, 18.672f)
            curveTo(15.832f, 18.799f, 16.316f, 18.992f, 16.627f, 19.229f)
            curveTo(16.906f, 19.443f, 16.871f, 19.66f, 16.821f, 19.748f)
            curveTo(16.601f, 20.133f, 13.031f, 19.993f, 12.001f, 19.874f)
            curveTo(10.971f, 19.993f, 7.401f, 20.133f, 7.181f, 19.748f)
            curveTo(7.131f, 19.66f, 7.096f, 19.443f, 7.375f, 19.229f)
            curveTo(7.685f, 18.992f, 8.169f, 18.799f, 8.564f, 18.672f)
            curveTo(7.51f, 17.749f, 7.022f, 16.47f, 7.022f, 15.491f)
            curveTo(7.022f, 15.491f, 6.016f, 17.182f, 5.668f, 17.139f)
            curveTo(5.506f, 17.119f, 5.29f, 16.211f, 5.951f, 14.023f)
            curveTo(6.088f, 13.563f, 6.278f, 13.028f, 6.469f, 12.514f)
            lineTo(7.165f, 10.718f)
            curveTo(7.165f, 10.697f, 7.155f, 10.343f, 7.155f, 10.16f)
            curveTo(7.155f, 7.088f, 8.558f, 4f, 12.004f, 4f)
            close()
        }
    }.build()

    // Bot 图标 (Lucide Bot)
    val Bot = ImageVector.Builder(
        name = "bot",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // path 1: M12 8V4H8 (手臂/天线)
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color(0xFF1C1C1E)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 8f)
            verticalLineTo(4f)
            horizontalLineTo(8f)
        }
        // path 2: rect (主身体)
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color(0xFF1C1C1E)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // rect x=4 y=8 width=16 height=12 rx=2
            moveTo(6f, 8f)
            horizontalLineTo(18f)
            arcTo(2f, 2f, 0f, false, true, 20f, 10f)
            verticalLineTo(18f)
            arcTo(2f, 2f, 0f, false, true, 18f, 20f)
            horizontalLineTo(6f)
            arcTo(2f, 2f, 0f, false, true, 4f, 18f)
            verticalLineTo(10f)
            arcTo(2f, 2f, 0f, false, true, 6f, 8f)
            close()
        }
        // path 3: M2 14h2 (左侧天线)
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color(0xFF1C1C1E)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(2f, 14f)
            horizontalLineTo(4f)
        }
        // path 4: M20 14h2 (右侧天线)
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color(0xFF1C1C1E)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(20f, 14f)
            horizontalLineTo(22f)
        }
        // path 5: M15 13v2 (右眼)
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color(0xFF1C1C1E)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(15f, 13f)
            verticalLineTo(15f)
        }
        // path 6: M9 13v2 (左眼)
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color(0xFF1C1C1E)),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(9f, 13f)
            verticalLineTo(15f)
        }
    }.build()
}
