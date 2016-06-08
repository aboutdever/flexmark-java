package com.vladsch.flexmark.parser;

import com.vladsch.flexmark.node.Paragraph;
import com.vladsch.flexmark.parser.block.ParserState;

public interface ParagraphProcessor {

    /**
     * Process Paragraph Content on closing of the paragraph block to remove non-text lines.
     * <p>
     * This is used by extensions to take leading lines from a paragraph and convert them
     * to other blocks
     * 
     * by Default leading lines that define references are removed and Reference nodes are inserted before.
     * 
     * @param block     paragraph node to process  
     * @param state     parser state
     */
    
    void processParagraph(Paragraph block, ParserState state);
}
