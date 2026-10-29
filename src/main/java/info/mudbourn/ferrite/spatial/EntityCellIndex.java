package info.mudbourn.ferrite.spatial;

// Switch, oracle rate and counters for the entity spatial query index.
public final class EntityCellIndex {

    public static final boolean ENABLED = !"false".equals(System.getProperty("ferrite.entityquery.cache"));
    public static final int ORACLE_RATE = Integer.getInteger("ferrite.entityquery.oracle", 16);

    public static long scanned;
    public static long filteredOut;
    public static long delivered;
    public static long oracleChecks;
    public static long oracleMismatches;
    public static int queryCounter;
    public static long typedQueries;
    public static long typedScanned;

    private EntityCellIndex() {
    }
}
