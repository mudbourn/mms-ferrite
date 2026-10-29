package info.mudbourn.ferrite.hopper;

// Container duck holding a lower bound on the first non-empty slot index.
public interface ExtractHint {

    int ferrite$getExtractHint();

    void ferrite$setExtractHint(int hint);
}
