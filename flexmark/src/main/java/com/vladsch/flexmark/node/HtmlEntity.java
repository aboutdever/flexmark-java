package com.vladsch.flexmark.node;

import com.vladsch.flexmark.internal.util.sequence.BasedSequence;

/**
 * Inline HTML element.
 *
 * @see <a href="http://spec.commonmark.org/0.24/#raw-html">CommonMark Spec</a>
 */
public class HtmlEntity extends Node {
    public interface Visitor {
        void visit(HtmlEntity node);
    }

    @Override
    public void getAstExtra(StringBuilder out) {
        if (!getChars().isEmpty()) out.append(" \"").append(getChars()).append("\"");
    }

    // TODO: add opening and closing marker with intermediate text so that completions can be easily done
    @Override
    public BasedSequence[] getSegments() {
        return EMPTY_SEGMENTS;
    }

    public HtmlEntity() {
    }

    public HtmlEntity(BasedSequence chars) {
        super(chars);
    }

}
