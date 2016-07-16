package com.vladsch.flexmark.node;

import com.vladsch.flexmark.internal.util.sequence.BasedSequence;

/**
 * Only generated for CharacterNodeFactory custom parsing
 */
public class WhiteSpace extends TextBase {
    public interface Visitor {
        void visit(WhiteSpace node);
    }


    public WhiteSpace() {
    }

    public WhiteSpace(BasedSequence chars) {
        super(chars);
    }

    public WhiteSpace(String chars) {
        super(chars);
    }
}
