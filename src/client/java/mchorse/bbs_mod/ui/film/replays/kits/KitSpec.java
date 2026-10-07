package mchorse.bbs_mod.ui.film.replays.kits;

public record KitSpec(
    ArmorPieceSpec head,
    ArmorPieceSpec chest,
    ArmorPieceSpec legs,
    ArmorPieceSpec feet,
    String mainHandId,
    String offHandId
)
{
}
