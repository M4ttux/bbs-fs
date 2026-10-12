package mchorse.bbs_mod.utils.clips;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** Plans positions before editing, so invalid ranges cannot partially move a selection. */
public final class ClipSpacing
{
    private ClipSpacing()
    {}

    public record Position(Clip clip, int tick)
    {}

    public static List<Position> plan(List<Clip> selection, int gap)
    {
        if (gap < 0)
        {
            throw new IllegalArgumentException("Gap must be nonnegative");
        }

        TreeMap<Integer, List<Clip>> groups = new TreeMap<>();

        for (Clip clip : selection)
        {
            groups.computeIfAbsent(clip.tick.get(), tick -> new ArrayList<>()).add(clip);
        }

        List<Position> positions = new ArrayList<>();
        long tick = groups.isEmpty() ? 0 : groups.firstKey();

        for (List<Clip> group : groups.values())
        {
            int duration = 0;

            for (Clip clip : group)
            {
                if (tick + clip.duration.get() > Integer.MAX_VALUE)
                {
                    throw new IllegalArgumentException("Selection exceeds the timeline range");
                }

                positions.add(new Position(clip, (int) tick));
                duration = Math.max(duration, clip.duration.get());
            }

            tick += (long) duration + gap;
        }

        return positions;
    }
}
