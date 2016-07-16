package com.vladsch.flexmark.node;

import com.vladsch.flexmark.internal.BlockContent;
import com.vladsch.flexmark.internal.util.sequence.BasedSequence;

import java.util.List;

public class Paragraph extends Block {
    private static int[] EMPTY_INDENTS = new int[0];
    private int[] lineIndents = EMPTY_INDENTS;

    @Override
    public BasedSequence[] getSegments() {
        return EMPTY_SEGMENTS;
    }

    public Paragraph() {
    }

    public Paragraph(BasedSequence chars) {
        super(chars);
    }

    public Paragraph(BasedSequence chars, List<BasedSequence> lineSegments, List<Integer> lineIndents) {
        super(chars, lineSegments);
        if (lineSegments.size() != lineIndents.size())
            throw new IllegalArgumentException("line segments and line indents have to be of the same size");
        setLineIndents(lineIndents);
    }

    public Paragraph(BasedSequence chars, List<BasedSequence> lineSegments, int[] lineIndents) {
        super(chars, lineSegments);
        if (lineSegments.size() != lineIndents.length)
            throw new IllegalArgumentException("line segments and line indents have to be of the same size");
        this.lineIndents = lineIndents;
    }

    public Paragraph(BlockContent blockContent) {
        super(blockContent);
        setLineIndents(blockContent.getLineIndents());
    }

    protected void setLineIndents(List<Integer> lineIndents) {
        this.lineIndents = new int[lineIndents.size()];
        int i = 0;
        for (int indent : lineIndents) {
            this.lineIndents[i++] = indent;
        }
    }

    @Override
    @Deprecated
    public void setContent(BasedSequence chars, List<BasedSequence> lineSegments) {
        super.setContent(chars, lineSegments);
    }

    public boolean isInTightListItem() {
        Node parent = getParent();
        return parent != null && parent instanceof ListItem && ((ListItem) parent).isParagraphInTightListItem();
    }

    public void setContent(BasedSequence chars, List<BasedSequence> lineSegments, List<Integer> lineIndents) {
        super.setContent(chars, lineSegments);
        if (lineSegments.size() != lineIndents.size())
            throw new IllegalArgumentException("line segments and line indents have to be of the same size");
        setLineIndents(lineIndents);
    }

    @Override
    @Deprecated
    public void setContent(List<BasedSequence> lineSegments) {
        super.setContent(lineSegments);
    }

    @Override
    public void setContent(BlockContent blockContent) {
        super.setContent(blockContent);
        setLineIndents(blockContent.getLineIndents());
    }

    public void setContent(BlockContent blockContent, int startLine, int endLine) {
        super.setContent(blockContent.getLines().subList(startLine, endLine));
        setLineIndents(blockContent.getLineIndents().subList(startLine, endLine));
    }

    public void setContent(Paragraph other, int startLine, int endLine) {
        super.setContent(other.getContentLines(startLine, endLine));
        if (endLine > startLine) {
            int[] lineIndents = new int[endLine - startLine];
            System.arraycopy(other.lineIndents, startLine, lineIndents, 0, endLine - startLine);
            this.lineIndents = lineIndents;
        } else {
            this.lineIndents = EMPTY_INDENTS;
        }
    }

    public void setLineIndents(int[] lineIndents) {
        this.lineIndents = lineIndents;
    }

    public int getLineIndent(int line) {
        return lineIndents[line];
    }

    public int[] getLineIndents() {
        return lineIndents;
    }

}
