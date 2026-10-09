package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.enableMarquee
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

/**
 * Displays an image as a card. If no image is available, the name will be shown instead
 */
@Composable
fun BannerCard(
    name: String?,
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerText: String? = null,
    played: Boolean = false,
    favorite: Boolean = false,
    playPercent: Double = 0.0,
    cardHeight: Dp = 120.dp,
    aspectRatio: Float = AspectRatios.WIDE,
    interactionSource: MutableInteractionSource? = null,
    imageType: ImageType = ImageType.PRIMARY,
    imageContentScale: ContentScale = ContentScale.FillBounds,
    useSeriesForPrimary: Boolean = true,
) {
    val imageUrlService = LocalImageUrlService.current
    val density = LocalDensity.current
    val widerImageRatio =
        remember(item, aspectRatio, imageType, useSeriesForPrimary) {
            widerOwnPrimaryRatio(item, aspectRatio, imageType, useSeriesForPrimary)
        }
    val cardRatio = widerImageRatio ?: aspectRatio
    val contentScale = if (widerImageRatio != null) ContentScale.Fit else imageContentScale
    val fillHeight =
        remember(cardHeight, density) {
            if (cardHeight.isSpecified) {
                with(density) {
                    cardHeight.roundToPx()
                }
            } else {
                null
            }
        }
    val imageUrl =
        remember(item, fillHeight, imageType, useSeriesForPrimary) {
            if (item != null) {
                item.imageUrlOverride
                    ?: imageUrlService.getItemImageUrl(
                        item,
                        imageType,
                        fillWidth = null,
                        fillHeight = fillHeight,
                        useSeriesForPrimary = useSeriesForPrimary,
                    )
            } else {
                null
            }
        }
    var imageError by remember(imageUrl) { mutableStateOf(false) }

    // Stabilize callbacks to prevent AsyncImage from recomposing
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    Card(
        modifier = modifier.size(cardHeight * cardRatio, cardHeight),
        onClick = { currentOnClick() },
        onLongClick = { currentOnLongClick() },
        interactionSource = interactionSource,
        border = libraryCardBorder(item),
        colors =
            CardDefaults.colors(
//                containerColor = Color.Transparent,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize(),
//                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (!imageError && imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = contentScale,
                    onError = remember { { imageError = true } },
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (item?.inLibrary == false) {
                NotInLibraryPlaceholder(title = item.name, year = item.data.productionYear)
            } else {
                Text(
                    text = name ?: "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .padding(16.dp)
                            .align(Alignment.Center),
                )
            }
            if (played || cornerText != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                ) {
                    if (played && (playPercent <= 0 || playPercent >= 100)) {
                        WatchedIcon(Modifier.size(24.dp))
                    }
                    if (cornerText != null) {
                        Box(
                            modifier =
                                Modifier
                                    .background(
                                        AppColors.TransparentBlack50,
                                        shape = RoundedCornerShape(25),
                                    ),
                        ) {
                            Text(
                                text = cornerText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(4.dp),
                            )
                        }
                    }
                }
            }
            if (favorite) {
                Text(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                    color = colorResource(android.R.color.holo_red_light),
                    text = stringResource(R.string.fa_heart),
                    fontSize = 16.sp,
                    fontFamily = FontAwesome,
                )
            }
            if (playPercent > 0 && playPercent < 100) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .background(
                                MaterialTheme.colorScheme.tertiary,
                            ).clip(RectangleShape)
                            .height(Cards.playedPercentHeight)
                            .fillMaxWidth((playPercent / 100).toFloat()),
                )
            }
        }
    }
}

@Composable
fun BannerCardWithTitle(
    title: String?,
    subtitle: String?,
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerText: String? = null,
    played: Boolean = false,
    favorite: Boolean = false,
    playPercent: Double = 0.0,
    cardHeight: Dp = 120.dp,
    aspectRatio: Float = AspectRatios.WIDE,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    imageType: ImageType = ImageType.PRIMARY,
    imageContentScale: ContentScale = ContentScale.FillBounds,
    useSeriesForPrimary: Boolean = item?.useSeriesForPrimary ?: true,
) {
    val focused by interactionSource.collectIsFocusedAsState()
    val focusedAfterDelay by rememberFocusedAfterDelay(interactionSource)
    val widerImageRatio =
        remember(item, aspectRatio, imageType, useSeriesForPrimary) {
            widerOwnPrimaryRatio(item, aspectRatio, imageType, useSeriesForPrimary)
        }
    val cardRatio = widerImageRatio ?: aspectRatio
    val aspectRationToUse = cardRatio.coerceAtLeast(AspectRatios.MIN)
    val width = cardHeight * aspectRationToUse
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.width(width),
    ) {
        BannerCard(
            // Shown when there is no image: the series of a season/episode rather than an empty card
            name = item?.data?.seriesName ?: item?.name,
            item = item,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier,
            cornerText = cornerText,
            played = played,
            favorite = favorite,
            playPercent = playPercent,
            cardHeight = cardHeight,
            aspectRatio = cardRatio,
            interactionSource = interactionSource,
            imageType = imageType,
            imageContentScale = if (widerImageRatio != null) ContentScale.Fit else imageContentScale,
            useSeriesForPrimary = useSeriesForPrimary,
        )
        SlidingCardText(focused) {
            Text(
                text = title ?: "",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .enableMarquee(focusedAfterDelay),
            )
            Text(
                text = subtitle ?: "",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .enableMarquee(focusedAfterDelay),
            )
        }
    }
}

/** How much wider than the card an item's own primary image must be to show it whole in a wider card */
private const val WIDER_IMAGE_THRESHOLD = 1.2f

/**
 * The aspect ratio of the item's own primary image (from [BaseItem.aspectRatio], ie `primaryImageAspectRatio`) when
 * it is clearly wider than [cardRatio], eg a 16:9 image of a series in a row of 2:3 posters. The card then keeps its
 * height, gets this width and shows the image whole (ContentScale.Fit) instead of cropping it. Null otherwise, so the
 * row's aspect ratio and content scale settings apply as before.
 */
fun widerOwnPrimaryRatio(
    item: BaseItem?,
    cardRatio: Float,
    imageType: ImageType,
    useSeriesForPrimary: Boolean,
): Float? {
    if (item == null || imageType != ImageType.PRIMARY || item.imageUrlOverride != null) return null
    if (useSeriesForPrimary && (item.type == BaseItemKind.EPISODE || item.type == BaseItemKind.SEASON)) return null
    // Only the item's own image has this aspect ratio, a series poster shown for a season does not
    if (item.data.imageTags
            ?.get(ImageType.PRIMARY)
            .isNullOrBlank()
    ) {
        return null
    }
    val ratio = item.aspectRatio ?: return null
    if (ratio <= cardRatio * WIDER_IMAGE_THRESHOLD) return null
    return ratio.coerceAtMost(maxOf(cardRatio, AspectRatios.WIDE))
}
