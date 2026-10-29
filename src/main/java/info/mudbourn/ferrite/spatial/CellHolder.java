package info.mudbourn.ferrite.spatial;

// Entity duck holding the cached packed block position, Long.MIN_VALUE when unset.
public interface CellHolder {

    long ferrite$packedPos();

    void ferrite$setPackedPos(long packed);
}
