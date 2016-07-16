package com.vladsch.flexmark.node;

import com.vladsch.flexmark.internal.util.sequence.BasedSequence;

public class Code extends DelimitedNodeImpl {
    public Code() {
    }

    public Code(BasedSequence chars) {
        super(chars);
    }

    public Code(BasedSequence openingMarker, BasedSequence content, BasedSequence closingMarker) {
        super(openingMarker, content, closingMarker);
    }

    @Override
    public void getAstExtra(StringBuilder out) {
        delimitedSegmentSpan(out, openingMarker, text, closingMarker, "text");
    }
}
