package info.mudbourn.ferrite.spatial;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;

// 64 cells of 4x4x4 blocks over one entity section, each a bitset of storage list indices.
public final class SectionGrid {

    private static final int CELLS = 64;

    private final int words;
    private final long[] bits;
    private final IdentityHashMap<Object, Integer> indexOf;
    private final int originX;
    private final int originY;
    private final int originZ;
    private boolean dirty;

    public interface Visitor {

        // Returns false to abort iteration.
        boolean visit(int listIndex);
    }

    public SectionGrid(int capacity, int originX, int originY, int originZ) {
        this.words = Math.max(1, (capacity + 63) >> 6);
        this.bits = new long[CELLS * words];
        this.indexOf = new IdentityHashMap<>(capacity * 2);
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
    }

    public boolean dirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public int capacity() {
        return words << 6;
    }

    private int cellOfPacked(long packed) {
        int lx = (BlockPos.getX(packed) - originX) & 15;
        int ly = (BlockPos.getY(packed) - originY) & 15;
        int lz = (BlockPos.getZ(packed) - originZ) & 15;
        return ((ly >> 2) << 4) | ((lz >> 2) << 2) | (lx >> 2);
    }

    public void rebuild(List<?> list) {
        Arrays.fill(bits, 0L);
        indexOf.clear();
        for (int i = 0; i < list.size() && i < capacity(); i++) {
            Object entity = list.get(i);
            indexOf.put(entity, i);
            long packed = entity instanceof CellHolder holder ? holder.ferrite$packedPos() : Long.MIN_VALUE;
            int cell = packed == Long.MIN_VALUE ? 0 : cellOfPacked(packed);
            bits[cell * words + (i >> 6)] |= 1L << (i & 63);
        }
        dirty = list.size() > capacity();
    }

    public void onAdd(Object entity, int listIndex, long packed) {
        if (dirty) {
            return;
        }
        if (listIndex >= capacity()) {
            dirty = true;
            return;
        }
        indexOf.put(entity, listIndex);
        int cell = packed == Long.MIN_VALUE ? 0 : cellOfPacked(packed);
        bits[cell * words + (listIndex >> 6)] |= 1L << (listIndex & 63);
    }

    public void onMove(Object entity, long oldPacked, long newPacked) {
        if (dirty) {
            return;
        }
        Integer index = indexOf.get(entity);
        if (index == null) {
            dirty = true;
            return;
        }
        int oldCell = oldPacked == Long.MIN_VALUE ? 0 : cellOfPacked(oldPacked);
        int newCell = newPacked == Long.MIN_VALUE ? 0 : cellOfPacked(newPacked);
        if (oldCell == newCell) {
            return;
        }
        int word = index >> 6;
        long bit = 1L << (index & 63);
        bits[oldCell * words + word] &= ~bit;
        bits[newCell * words + word] |= bit;
    }

    // Visits indices in ascending order, which is vanilla list order; false means the visitor aborted.
    public boolean query(int minLX, int minLY, int minLZ, int maxLX, int maxLY, int maxLZ, Visitor visitor) {
        if (maxLX < 0 || maxLY < 0 || maxLZ < 0 || minLX > 15 || minLY > 15 || minLZ > 15) {
            return true;
        }
        int cx0 = Math.max(minLX, 0) >> 2;
        int cx1 = Math.min(maxLX, 15) >> 2;
        int cy0 = Math.max(minLY, 0) >> 2;
        int cy1 = Math.min(maxLY, 15) >> 2;
        int cz0 = Math.max(minLZ, 0) >> 2;
        int cz1 = Math.min(maxLZ, 15) >> 2;
        for (int w = 0; w < words; w++) {
            long mask = 0L;
            for (int cy = cy0; cy <= cy1; cy++) {
                for (int cz = cz0; cz <= cz1; cz++) {
                    int rowBase = (cy << 4) | (cz << 2);
                    for (int cx = cx0; cx <= cx1; cx++) {
                        mask |= bits[(rowBase | cx) * words + w];
                    }
                }
            }
            int base = w << 6;
            while (mask != 0L) {
                int bit = Long.numberOfTrailingZeros(mask);
                mask &= mask - 1;
                if (!visitor.visit(base + bit)) {
                    return false;
                }
            }
        }
        return true;
    }
}
