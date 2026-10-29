package info.mudbourn.ferrite.spatial;

// EntitySection duck tracking the largest member box, its origin and its bitset grid.
public interface SectionExtents {

    float ferrite$maxHalfXZ();

    float ferrite$maxHeight();

    void ferrite$growExtents(float halfXZ, float height);

    void ferrite$setOrigin(int x, int y, int z);

    int ferrite$originX();

    int ferrite$originY();

    int ferrite$originZ();

    SectionGrid ferrite$grid();

    void ferrite$setGrid(SectionGrid grid);
}
