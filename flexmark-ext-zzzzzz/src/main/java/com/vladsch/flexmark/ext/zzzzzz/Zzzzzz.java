package com.vladsch.flexmark.ext.zzzzzz;

import com.vladsch.flexmark.internal.util.sequence.BasedSequence;
import com.vladsch.flexmark.internal.util.sequence.SubSequence;
import com.vladsch.flexmark.node.CustomNode;
import com.vladsch.flexmark.node.DelimitedNode;
import com.vladsch.flexmark.node.DoNotLinkify;

/**
 * A Zzzzzz node
 */
public class Zzzzzz extends CustomNode implements DelimitedNode, DoNotLinkify {
    protected BasedSequence openingMarker = SubSequence.NULL;
    protected BasedSequence text = SubSequence.NULL;
    protected BasedSequence closingMarker = SubSequence.NULL;
    protected ZzzzzzBlock zzzzzzBlock;
    protected String zzzzzzBlockText;

    public ZzzzzzBlock getZzzzzzBlock() {
        return zzzzzzBlock;
    }

    public void setZzzzzzBlock(ZzzzzzBlock zzzzzzBlock) {
        this.zzzzzzBlock = zzzzzzBlock;
    }

    @Override
    public BasedSequence[] getSegments() {
        //return EMPTY_SEGMENTS;
        return new BasedSequence[] { openingMarker, text, closingMarker };
    }

    @Override
    public void getAstExtra(StringBuilder out) {
        out.append(" ordinal: ").append(zzzzzzBlock != null ? zzzzzzBlock.getZzzzzzOrdinal() : 0).append(" ");
        delimitedSegmentSpanChars(out, openingMarker, text, closingMarker, "text");
    }

    public Zzzzzz() {
    }

    public Zzzzzz(BasedSequence chars) {
        super(chars);
    }

    public Zzzzzz(BasedSequence openingMarker, BasedSequence text, BasedSequence closingMarker) {
        super(new SubSequence(openingMarker.getBase(), openingMarker.getStartOffset(), closingMarker.getEndOffset()));
        this.openingMarker = openingMarker;
        this.text = text;
        this.closingMarker = closingMarker;
    }

    public Zzzzzz(BasedSequence chars, String zzzzzzBlockText) {
        super(chars);
        this.zzzzzzBlockText = zzzzzzBlockText;
    }

    public BasedSequence getOpeningMarker() {
        return openingMarker;
    }

    public void setOpeningMarker(BasedSequence openingMarker) {
        this.openingMarker = openingMarker;
    }

    public BasedSequence getText() {
        return text;
    }

    public void setText(BasedSequence text) {
        this.text = text;
    }

    public BasedSequence getClosingMarker() {
        return closingMarker;
    }

    public void setClosingMarker(BasedSequence closingMarker) {
        this.closingMarker = closingMarker;
    }

    public void accept(ZzzzzzVisitor visitor) {
        visitor.visit(this);
    }
}
