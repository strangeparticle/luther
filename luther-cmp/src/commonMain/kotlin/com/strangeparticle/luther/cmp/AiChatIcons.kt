package com.strangeparticle.luther.cmp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// The fixed set of Material icons the drop-in chat component renders, hand-bundled as ImageVectors
// from the official Material Icons SVG path data. luther-cmp owns its own icons rather than
// depending on a host icon set or the deprecated material-icons artifacts, so the component stays
// self-contained. Vectors are built lazily so only icons actually rendered are constructed.
private fun aiChatIconVector(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()

internal object AiChatIcons {
    val ArrowDropDown: ImageVector by lazy {
        aiChatIconVector("ArrowDropDown", "m7 10l5 5l5-5z")
    }

    val BugReport: ImageVector by lazy {
        aiChatIconVector(
            name = "BugReport",
            pathData = "M20 8h-2.81a6 6 0 0 0-1.82-1.96L17 4.41L15.59 3l-2.17 2.17" +
                "C12.96 5.06 12.49 5 12 5s-.96.06-1.41.17L8.41 3L7 4.41l1.62 1.63" +
                "C7.88 6.55 7.26 7.22 6.81 8H4v2h2.09c-.05.33-.09.66-.09 1v1H4v2h2v1" +
                "c0 .34.04.67.09 1H4v2h2.81c1.04 1.79 2.97 3 5.19 3s4.15-1.21 5.19-3H20v-2" +
                "h-2.09c.05-.33.09-.66.09-1v-1h2v-2h-2v-1c0-.34-.04-.67-.09-1H20z" +
                "m-6 8h-4v-2h4zm0-4h-4v-2h4z",
        )
    }

    val Close: ImageVector by lazy {
        aiChatIconVector(
            name = "Close",
            pathData = "M19 6.41L17.59 5L12 10.59L6.41 5L5 6.41L10.59 12L5 17.59L6.41 19" +
                "L12 13.41L17.59 19L19 17.59L13.41 12z",
        )
    }

    val ContentCopy: ImageVector by lazy {
        aiChatIconVector(
            name = "ContentCopy",
            pathData = "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12zm3 4H8c-1.1 0-2 .9-2 2v14" +
                "c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2m0 16H8V7h11z",
        )
    }

    val DragHandle: ImageVector by lazy {
        aiChatIconVector("DragHandle", "M20 9H4v2h16zM4 15h16v-2H4z")
    }

    val Refresh: ImageVector by lazy {
        aiChatIconVector(
            name = "Refresh",
            pathData = "M17.65 6.35A7.96 7.96 0 0 0 12 4c-4.42 0-7.99 3.58-7.99 8" +
                "s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08A5.99 5.99 0 0 1 12 18" +
                "c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4z",
        )
    }
}
