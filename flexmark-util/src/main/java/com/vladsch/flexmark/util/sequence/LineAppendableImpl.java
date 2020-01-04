package com.vladsch.flexmark.util.sequence;

import com.vladsch.flexmark.util.collection.iteration.Indexed;
import com.vladsch.flexmark.util.collection.iteration.IndexedItemIterable;
import com.vladsch.flexmark.util.collection.iteration.IndexedItemIterator;
import com.vladsch.flexmark.util.misc.BitFieldSet;
import com.vladsch.flexmark.util.misc.Pair;
import com.vladsch.flexmark.util.sequence.builder.ISequenceBuilder;
import com.vladsch.flexmark.util.sequence.builder.StringSequenceBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Stack;

import static com.vladsch.flexmark.util.misc.Utils.*;
import static com.vladsch.flexmark.util.sequence.SequenceUtils.isBlank;
import static com.vladsch.flexmark.util.sequence.SequenceUtils.*;
import static java.lang.Integer.MAX_VALUE;

public class LineAppendableImpl implements LineAppendable {
    final private static char EOL = '\n';

    final private boolean passThrough;              // pass through mode for all operations to appendable without examination
    final private BitFieldSet<Options> options;

    // pre-formatted nesting level, while >0 all text is passed through as is and not monitored
    private int preFormattedNesting;
    private int preFormattedFirstLine;        // line which should be prefixed
    private int preFormattedFirstLineOffset;  // first line start of preformatted offset
    private int preFormattedLastLine;         // last line of preformatted text
    private int preFormattedLastLineOffset;   // last line end of preformatted offset

    // accumulated text and line information
    private ISequenceBuilder<?, ?> appendable;
    final ArrayList<LineInfo> lines;  // line contents

    // indent level to use after the next \n and before text is appended
    private CharSequence prefix;                     // current prefix
    private CharSequence prefixAfterEol;             // next prefix after eol
    private CharSequence indentPrefix;               // indent prefix
    final private Stack<CharSequence> prefixStack;
    final private Stack<Boolean> indentPrefixStack;

    // current line being accumulated
    private boolean allWhitespace;                          // all chars were whitespace
    private boolean lastWasWhitespace;                      // last char was whitespace
    private int eolOnFirstText;                             // append EOLs on first text
    final private ArrayList<Runnable> indentsOnFirstEol;    // append indents on first eol
    final private Stack<Integer> optionStack = new Stack<>();
    int modificationCount;                          // mod count for iterable use

    public LineAppendableImpl(int formatOptions) {
        this(null, LineAppendable.toOptionSet(formatOptions));
    }

    public LineAppendableImpl(@Nullable Appendable builder, int formatOptions) {
        this(builder, LineAppendable.toOptionSet(formatOptions));
    }

    public LineAppendableImpl(@Nullable Appendable appendable, BitFieldSet<Options> formatOptions) {
        this.appendable = appendable instanceof ISequenceBuilder<?, ?> ? ((ISequenceBuilder<?, ?>) appendable).getBuilder()
                : appendable instanceof LineAppendable ? ((LineAppendable) appendable).getBuilder()
                : StringSequenceBuilder.emptyBuilder();

        options = formatOptions;
        passThrough = any(F_PASS_THROUGH);
        preFormattedNesting = 0;
        preFormattedFirstLine = -1;
        preFormattedLastLine = -1;
        allWhitespace = true;
        lastWasWhitespace = false;
        lines = new ArrayList<>();
        prefixStack = new Stack<>();
        indentPrefixStack = new Stack<>();
        prefix = BasedSequence.EMPTY;
        prefixAfterEol = BasedSequence.EMPTY;
        indentPrefix = BasedSequence.EMPTY;
        eolOnFirstText = 0;
        indentsOnFirstEol = new ArrayList<>();
    }

    @Override
    public @NotNull LineAppendable getEmptyAppendable() {
        return new LineAppendableImpl(this, getOptions());
    }

    @NotNull
    @Override
    public BitFieldSet<Options> getOptionSet() {
        return options;
    }

    @NotNull
    @Override
    public LineAppendable setOptions(int flags) {
        options.setAll(flags);
        return this;
    }

    @Override
    public @NotNull LineAppendable pushOptions() {
        optionStack.push(options.toInt());
        return this;
    }

    @Override
    public @NotNull LineAppendable popOptions() {
        if (optionStack.isEmpty()) {
            throw new IllegalStateException("Option stack is empty");
        }
        Integer mask = optionStack.pop();
        options.setAll(mask);
        return this;
    }

    @Override
    public @NotNull LineAppendable changeOptions(int addFlags, int removeFlags) {
        if ((addFlags & removeFlags) != 0) {
            throw new IllegalStateException(String.format("Add flags:%d and remove flags:%d overlap:%d", addFlags, removeFlags, addFlags & removeFlags));
        }
        options.orMask(addFlags);
        options.andNotMask(removeFlags);
        return this;
    }

    private boolean any(int flags) {
        return options.any(flags);
    }

    private boolean isConvertingTabs() {
        return any(F_CONVERT_TABS | F_COLLAPSE_WHITESPACE);
    }

    private boolean isTrimTrailingWhitespace() {
        return any(F_TRIM_TRAILING_WHITESPACE);
    }

    private boolean isTrimLeadingWhitespace() {
        return any(F_TRIM_LEADING_WHITESPACE);
    }

    private boolean isCollapseWhitespace() {
        return any(F_COLLAPSE_WHITESPACE);
    }

    @NotNull
    @Override
    public BasedSequence getIndentPrefix() {
        return BasedSequence.of(indentPrefix);
    }

    @NotNull
    @Override
    public LineAppendable setIndentPrefix(@Nullable CharSequence prefix) {
        indentPrefix = prefix == null ? BasedSequence.NULL : prefix;
        return this;
    }

    @Override
    public @NotNull BasedSequence getPrefix() {
        return BasedSequence.of(prefixAfterEol);
    }

    @Override
    public @NotNull BasedSequence getBeforeEolPrefix() {
        return BasedSequence.of(prefix);
    }

    private CharSequence combinedPrefix(@Nullable CharSequence prefix, @Nullable CharSequence suffix) {
        if (prefix != null && prefix.length() > 0 && suffix != null && suffix.length() > 0) {
            return String.valueOf(prefix) + suffix;
        } else if (prefix != null && prefix.length() > 0) {
            return prefix;
        } else if (suffix != null && suffix.length() > 0) {
            return suffix;
        } else {
            return BasedSequence.NULL;
        }
    }

    @NotNull
    @Override
    public LineAppendable addPrefix(@NotNull CharSequence prefix, boolean afterEol) {
        if (!passThrough) {
            if (afterEol) {
                prefixAfterEol = combinedPrefix(prefixAfterEol, prefix);
            } else {
                this.prefix = combinedPrefix(prefixAfterEol, prefix);
                prefixAfterEol = this.prefix;
            }
        }
        return this;
    }

    public int getAfterEolPrefixDelta() {
        return prefixAfterEol.length() - prefix.length();
    }

    @NotNull
    @Override
    public LineAppendable setPrefix(@Nullable CharSequence prefix, boolean afterEol) {
        if (!passThrough) {
            if (afterEol) {
                prefixAfterEol = prefix == null ? BasedSequence.NULL : prefix;
            } else {
                this.prefix = prefix == null ? BasedSequence.NULL : prefix;
                prefixAfterEol = this.prefix;
            }
        }
        return this;
    }

    @NotNull
    @Override
    public LineAppendable indent() {
        if (!passThrough) {
            line();
            rawIndent();
        }
        return this;
    }

    private void rawIndent() {
        prefixStack.push(prefixAfterEol);
        prefix = combinedPrefix(prefixAfterEol, indentPrefix);
        prefixAfterEol = prefix;
        indentPrefixStack.push(true);
    }

    private void rawUnIndent() {
        if (prefixStack.isEmpty()) throw new IllegalStateException("unIndent with an empty stack");
        if (!indentPrefixStack.peek()) throw new IllegalStateException("unIndent for an element added by pushPrefix(), use popPrefix() instead");

        prefix = prefixStack.pop();
        prefixAfterEol = prefix;
        indentPrefixStack.pop();
    }

    @NotNull
    @Override
    public LineAppendable unIndent() {
        if (!passThrough) {
            line();
            rawUnIndent();
        }
        return this;
    }

    @NotNull
    @Override
    public LineAppendable unIndentNoEol() {
        if (!passThrough) {
            if (isLastEol()) {
                rawUnIndent();
            } else {
                CharSequence prefix = this.prefix;
                rawUnIndent();
                prefixAfterEol = this.prefix;
                this.prefix = prefix;
            }
        }
        return this;
    }

    @NotNull
    @Override
    public LineAppendable pushPrefix() {
        if (!passThrough) {
            prefixStack.push(prefixAfterEol);
            indentPrefixStack.push(false);
        }
        return this;
    }

    @NotNull
    @Override
    public LineAppendable popPrefix(boolean afterEol) {
        if (!passThrough) {
            if (prefixStack.isEmpty()) throw new IllegalStateException("popPrefix with an empty stack");
            if (indentPrefixStack.peek()) throw new IllegalStateException("popPrefix for element added by indent(), use unIndent() instead");

            prefixAfterEol = prefixStack.pop();
            if (!afterEol) {
                prefix = prefixAfterEol;
            }
            indentPrefixStack.pop();
        }
        return this;
    }

    @NotNull
    LineInfo getLastLineInfo() {
        return lines.isEmpty() ? LineInfo.NULL : lines.get(lines.size() - 1);
    }

    private boolean isTrailingBlankLine() {
        return appendable.length() == 0 && getLastLineInfo().isBlankText();
    }

    int lastNonBlankLine(int endLine) {
        if (endLine > lines.size() && appendable.length() > 0 && !allWhitespace) {
            // have dangling text
            return lines.size();
        }

        int i = Math.min(lines.size(), endLine);
        while (i-- > 0) {
            LineInfo lineInfo = lines.get(i);
            if (!lineInfo.isBlankText()) break;
        }
        return i;
    }

    int trailingBlankLines(int endLine) {
        endLine = Math.min(lines.size(), endLine);
        return endLine - lastNonBlankLine(endLine) - 1;
    }

    private boolean isLastEol() {
        return appendable.length() == 0 && getLastLineInfo().isNotNull();
    }

    private LineInfo getLineRange(int start, int end, CharSequence prefix) {
        assert start <= end;

        CharSequence sequence = appendable.toSequence();
        CharSequence text = start == Range.NULL.getStart() && end == Range.NULL.getEnd() ? BasedSequence.NULL : sequence.subSequence(start, end);
        CharSequence eol = trimmedEOL(sequence);

        if (eol == null || eol.length() == 0) {
            eol = SequenceUtils.EOL;
        }

        if (start >= end) {
            prefix = SequenceUtils.trimEnd(prefix);
        }

        CharSequence line = appendable.getBuilder().append(prefix).append(text).append(eol).toSequence();

        LineInfo.Preformatted preformatted;

        if (preFormattedNesting > 0) {
            preformatted = preFormattedFirstLine == lines.size() ? LineInfo.Preformatted.FIRST : LineInfo.Preformatted.BODY;
        } else {
            preformatted = preFormattedFirstLine == lines.size() ? LineInfo.Preformatted.LAST : LineInfo.Preformatted.NONE;
        }

        return LineInfo.create(line, getLastLineInfo(), prefix.length(), text.length(), line.length(), isBlank(prefix), allWhitespace || text.length() == 0, preformatted);
    }

    private void resetBuilder() {
        appendable = appendable.getBuilder();
        allWhitespace = true;
        lastWasWhitespace = true;
    }

    private void addLineRange(int start, int end, CharSequence prefix) {
        lines.add(getLineRange(start, end, prefix));
        resetBuilder();
    }

    private void appendEol() {
        appendable.append(EOL);
        int endOffset = appendable.length();
        addLineRange(0, endOffset - 1, prefix);
        eolOnFirstText = 0;

        rawIndentsOnFirstEol();
    }

    private void rawIndentsOnFirstEol() {
        prefix = prefixAfterEol;

        while (!indentsOnFirstEol.isEmpty()) {
            Runnable runnable = indentsOnFirstEol.remove(indentsOnFirstEol.size() - 1);
            rawIndent();
            runnable.run();
        }
    }

    private void appendEol(int count) {
        while (count-- > 0) {
            appendEol();
        }
    }

    private boolean isPrefixed(int currentLine) {
        return any(F_PREFIX_PRE_FORMATTED) || (preFormattedFirstLine == currentLine || preFormattedNesting == 0 && preFormattedLastLine != currentLine);
    }

    /**
     * Returns text range if EOL was appended
     * <p>
     * NOTE: if range == Range.NULL then no line would be added
     *
     * @return pair of line text range if EOL was added and prefix
     */
    private Pair<Range, CharSequence> getRangePrefixAfterEol() {
        int startOffset = 0;
        int endOffset = appendable.length() + 1;
        int currentLine = lines.size();
        boolean needPrefix = isPrefixed(currentLine);

        if (passThrough) {
            return new Pair<>(Range.of(startOffset, endOffset - 1), needPrefix ? prefix : BasedSequence.NULL);
        } else {
            if (allWhitespace && (preFormattedNesting == 0 && !(preFormattedFirstLine == currentLine || preFormattedLastLine == currentLine))) {
                if (any(F_ALLOW_LEADING_EOL) || !lines.isEmpty()) {
                    return new Pair<>(Range.of(startOffset, endOffset - 1), prefix);
                } else {
                    return new Pair<>(Range.NULL, BasedSequence.NULL);
                }
            } else {
                // apply options other than convert tabs which is done at time of appending
                if (isTrimTrailingWhitespace() && preFormattedNesting == 0) {
                    if (allWhitespace) {
                        startOffset = endOffset - 1;
                    } else {
                        endOffset -= countTrailingSpaceTab(appendable.toSequence(), endOffset - 1);
                    }
                }

                if (preFormattedFirstLine == currentLine) {
                    if (startOffset > preFormattedFirstLineOffset) startOffset = preFormattedFirstLineOffset;
                }

                if (preFormattedLastLine == currentLine) {
                    if (endOffset < preFormattedLastLineOffset + 1) endOffset = preFormattedLastLineOffset + 1;
                }

                return new Pair<>(Range.of(startOffset, endOffset - 1), needPrefix ? prefix : BasedSequence.NULL);
            }
        }
    }

    /**
     * Returns text offset before EOL if EOL was issued
     *
     * @return would be offset after adding EOL - 1
     */
    private int offsetAfterEol() {
        Pair<Range, CharSequence> rangePrefixAfterEol = getRangePrefixAfterEol();
        LineInfo lastLineInfo = getLastLineInfo();

        if (rangePrefixAfterEol.getFirst().isNull()) {
            return lastLineInfo.sumLength;
        } else {
            Range range = rangePrefixAfterEol.getFirst();
            CharSequence prefix = rangePrefixAfterEol.getSecond();

            if (range.isEmpty() && prefix.length() != 0) {
                prefix = trimEnd(prefix);
            }

            return lastLineInfo.sumLength + rangePrefixAfterEol.getFirst().getSpan() + prefix.length();
        }
    }

    private void doEolOnFirstTest() {
        if (eolOnFirstText > 0) {
            eolOnFirstText = 0;
            appendEol();
        }
    }

    private void appendImpl(CharSequence s, int index) {
        char c = s.charAt(index);

        if (passThrough) {
            if (c == EOL) {
                appendEol();
            } else {
                if (eolOnFirstText > 0) {
                    eolOnFirstText = 0;
                    appendEol();
                }

                if (c != '\t' && c != ' ') allWhitespace = false;
                appendable.append(c);
            }
        } else {
            if (c == EOL) {
                Pair<Range, CharSequence> rangePrefixAfterEol = getRangePrefixAfterEol();
                Range textRange = rangePrefixAfterEol.getFirst();
                if (textRange.isNull()) {
                    // nothing to add, just add EOL
                    resetBuilder();
                } else {
                    // add EOL and line
                    appendable.append(c);
                    addLineRange(textRange.getStart(), textRange.getEnd(), rangePrefixAfterEol.getSecond());
                }

                rawIndentsOnFirstEol();
            } else {
                doEolOnFirstTest();

                if (c == '\t') {
                    if (preFormattedNesting == 0 && any(F_COLLAPSE_WHITESPACE)) {
                        if (!lastWasWhitespace) {
                            appendable.append(' ');
                            lastWasWhitespace = true;
                        }
                    } else {
                        if (any(F_CONVERT_TABS)) {
                            int column = appendable.length();
                            int spaces = 4 - (column % 4);
                            appendable.append(' ', spaces);
                        } else {
                            appendable.append(s, index, index + 1);
                        }
                    }
                } else {
                    if (c == ' ') {
                        if (preFormattedNesting == 0) {
                            if (!any(F_TRIM_LEADING_WHITESPACE) || (appendable.length() != 0 && !allWhitespace)) {
                                if (any(F_COLLAPSE_WHITESPACE)) {
                                    if (!lastWasWhitespace) {
                                        appendable.append(' ');
                                    }
                                } else {
                                    appendable.append(' ');
                                }
                            }
                        } else {
                            appendable.append(' ');
                        }
                        lastWasWhitespace = true;
                    } else {
                        allWhitespace = false;
                        lastWasWhitespace = false;
                        appendable.append(s, index, index + 1);
                    }
                }
            }
        }
    }

    private void appendImpl(CharSequence csq, int start, int end) {
        int i = start;
        while (i < end) {
            appendImpl(csq, i++);
        }
    }

    @NotNull
    @Override
    public LineAppendable append(@NotNull CharSequence csq) {
        appendImpl(csq, 0, csq.length());
        return this;
    }

    @NotNull
    @Override
    public ISequenceBuilder<?, ?> getBuilder() {
        return appendable.getBuilder();
    }

    @NotNull
    @Override
    public LineAppendable append(@NotNull CharSequence csq, int start, int end) {
        appendImpl(csq, start, end);
        return this;
    }

    @NotNull
    @Override
    public LineAppendable append(char c) {
        appendImpl(Character.toString(c), 0);
        return this;
    }

    @NotNull
    public LineAppendable append(char c, int count) {
        append(RepeatedSequence.repeatOf(c, count));
        return this;
    }

    @NotNull
    public LineAppendable repeat(@NotNull CharSequence csq, int count) {
        append(RepeatedSequence.repeatOf(csq, count));
        return this;
    }

    @NotNull
    public LineAppendable repeat(@NotNull CharSequence csq, int start, int end, int count) {
        append(RepeatedSequence.repeatOf(csq.subSequence(start, end), count));
        return this;
    }

    @NotNull
    @Override
    public LineAppendable line() {
        if (preFormattedNesting > 0 || appendable.length() != 0) {
            appendImpl(SequenceUtils.EOL, 0);
        } else {
            rawIndentsOnFirstEol();
        }
        return this;
    }

    @NotNull
    @Override
    public LineAppendable lineWithTrailingSpaces(int count) {
        if (preFormattedNesting > 0 || appendable.length() != 0) {
            int options = this.options.toInt();
            this.options.andNotMask(F_TRIM_TRAILING_WHITESPACE | F_COLLAPSE_WHITESPACE);
            if (count > 0) append(' ', count);
            appendImpl(SequenceUtils.EOL, 0);
            this.options.setAll(options);
        }
        return this;
    }

    @NotNull
    @Override
    public LineAppendable lineIf(boolean predicate) {
        if (predicate) line();
        return this;
    }

    @NotNull
    @Override
    public LineAppendable blankLine() {
        line();
        if (!lines.isEmpty() && !isTrailingBlankLine() || lines.isEmpty() && any(F_ALLOW_LEADING_EOL)) appendEol();
        return this;
    }

    @NotNull
    @Override
    public LineAppendable blankLineIf(boolean predicate) {
        if (predicate) blankLine();
        return this;
    }

    @NotNull
    @Override
    public LineAppendable blankLine(int count) {
        line();
        if ((any(F_ALLOW_LEADING_EOL) || !lines.isEmpty())) {
            int addBlankLines = count - trailingBlankLines(lines.size());
            appendEol(addBlankLines);
        }
        return this;
    }

    @NotNull
    @Override
    public LineAppendable lineOnFirstText(boolean value) {
        if (value) eolOnFirstText++;
        else if (eolOnFirstText > 0) eolOnFirstText--;
        return this;
    }

    @NotNull
    @Override
    public LineAppendable removeIndentOnFirstEOL(@NotNull Runnable listener) {
        indentsOnFirstEol.remove(listener);
        return this;
    }

    @NotNull
    @Override
    public LineAppendable addIndentOnFirstEOL(@NotNull Runnable listener) {
        indentsOnFirstEol.add(listener);
        return this;
    }

    @Override
    public int getLineCount() {
        return lines.size();
    }

    @Override
    public int size() {
        return appendable.length() == 0 ? lines.size() : lines.size() + 1;
    }

    @Override
    public int column() {
        return appendable.length();
    }

    @NotNull
    @Override
    public LineInfo getLineInfo(int lineIndex) {
        if (lineIndex == lines.size()) {
            if (appendable.length() == 0) {
                return LineInfo.NULL;
            } else {
                // create a dummy line info
                Pair<Range, CharSequence> rangePrefixAfterEol = getRangePrefixAfterEol();
                Range textRange = rangePrefixAfterEol.getFirst();

                if (textRange.isNull()) {
                    return LineInfo.NULL;
                } else {
                    return getLineRange(textRange.getStart(), textRange.getEnd(), rangePrefixAfterEol.getSecond());
                }
            }
        } else {
            return lines.get(lineIndex);
        }
    }

    @Override
    public @NotNull BasedSequence getLine(int lineIndex) {
        return getLineInfo(lineIndex).getLine();
    }

    @Override
    public int offset() {
        return getLastLineInfo().sumLength;
    }

    @Override
    public int offsetWithPending() {
        return offsetAfterEol();
    }

    @Override
    public boolean isPendingSpace() {
        return appendable.length() > 0 && lastWasWhitespace;
    }

    @Override
    public int getPendingSpace() {
        if (lastWasWhitespace && appendable.length() != 0) {
            return SequenceUtils.countTrailingSpaceTab(appendable.toSequence());
        }
        return 0;
    }

    @Override
    public int getPendingEOL() {
        if (appendable.length() == 0) {
            // use count of blank lines+1
            return trailingBlankLines(lines.size()) + 1;
        } else {
            return 0;
        }
    }

    @Override
    public boolean isPreFormatted() {
        return preFormattedNesting > 0;
    }

    @NotNull
    @Override
    public LineAppendable openPreFormatted(boolean addPrefixToFirstLine) {
        if (preFormattedNesting == 0) {
            if (preFormattedFirstLine != lines.size()) {
                preFormattedFirstLine = lines.size();
                preFormattedFirstLineOffset = appendable.length();
            }
        }

        ++preFormattedNesting;
        return this;
    }

    @NotNull
    @Override
    public LineAppendable closePreFormatted() {
        if (preFormattedNesting <= 0) throw new IllegalStateException("closePreFormatted called with nesting == 0");
        --preFormattedNesting;

        if (preFormattedNesting == 0 && !isLastEol()) {
            // this will be the last line of preformatted text
            preFormattedLastLine = lines.size();
            preFormattedLastLineOffset = appendable.length();
        }
        return this;
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder();
        try {
            appendToNoLine(out, true, Integer.MAX_VALUE, Integer.MAX_VALUE, 0, Integer.MAX_VALUE);
            out.append(appendable);
        } catch (IOException ignored) {

        }
        return out.toString();
    }

    @NotNull
    @Override
    public String toString(boolean withPrefixes, int maxBlankLines, int maxTrailingBlankLines) {
        StringBuilder out = new StringBuilder();
        try {
            appendTo(out, withPrefixes, maxBlankLines, maxTrailingBlankLines, 0, MAX_VALUE);
        } catch (IOException ignored) {

        }
        return out.toString();
    }

    @NotNull
    @Override
    public CharSequence toSequence(boolean withPrefixes, int maxBlankLines, int maxTrailingBlankLines) {
        ISequenceBuilder<?, ?> out = getBuilder();
        try {
            appendTo(out, withPrefixes, maxBlankLines, maxTrailingBlankLines, 0, MAX_VALUE);
        } catch (IOException ignored) {

        }
        return out.toSequence();
    }

    @Override
    public <T extends Appendable> T appendTo(@NotNull T out, boolean withPrefixes, int maxBlankLines, int maxTrailingBlankLines, int startLine, int endLine) throws IOException {
        line();
        return appendToNoLine(out, withPrefixes, maxBlankLines, maxTrailingBlankLines, startLine, endLine);
    }

    public <T extends Appendable> T appendToNoLine(@NotNull T out, boolean withPrefixes, int maxBlankLines, int maxTrailingBlankLines, int startLine, int endLine) throws IOException {
        boolean tailEOL = maxTrailingBlankLines >= 0;
        maxBlankLines = Math.max(0, maxBlankLines);
        maxTrailingBlankLines = Math.max(0, maxTrailingBlankLines);

        int iMax = min(size(), endLine);
        int lastNonBlankLine = lastNonBlankLine(iMax);
        int consecutiveBlankLines = 0;

        for (int i = startLine; i < iMax; i++) {
            LineInfo info = getLineInfo(i);

            if (info.isBlankText() && !info.isPreformatted()) {
                if (i > lastNonBlankLine) {
                    // NOTE: these are tail blank lines
                    if (consecutiveBlankLines < maxTrailingBlankLines) {
                        consecutiveBlankLines++;
                        if (withPrefixes) out.append(trimEnd(info.getPrefix()));
                        if (tailEOL || consecutiveBlankLines != maxTrailingBlankLines) {
                            out.append(EOL);
                        }
                    }
                } else {
                    if (consecutiveBlankLines < maxBlankLines) {
                        consecutiveBlankLines++;
                        if (withPrefixes) out.append(trimEnd(info.getPrefix()));
                        out.append(EOL);
                    }
                }
            } else {
                consecutiveBlankLines = 0;
                if (tailEOL || i < lastNonBlankLine || info.isPreformatted() && info.getPreformatted() != LineInfo.Preformatted.LAST) {
                    if (withPrefixes) out.append(info.line);
                    else out.append(info.getText());
                } else {
                    if (withPrefixes) out.append(info.getTextNoEOL());
                    else out.append(info.getText());
                }
            }
        }
        return out;
    }

    @NotNull
    @Override
    public LineAppendable append(@NotNull LineAppendable lineAppendable, int startLine, int endLine) {
        int iMax = lineAppendable.size();
        for (int i = 0; i < iMax; i++) {
            LineInfo info = lineAppendable.getLineInfo(i);
            CharSequence text = info.getTextNoEOL();
            CharSequence prefix = info.getPrefix();

            appendable.append(text);
            appendable.append(EOL);
            allWhitespace = info.isBlankText();

            int endOffset = appendable.length();
            CharSequence combinedPrefix = any(F_PREFIX_PRE_FORMATTED) || !info.isPreformatted() || info.getPreformatted() == LineInfo.Preformatted.FIRST ? combinedPrefix(this.prefix, prefix) : BasedSequence.NULL;
            addLineRange(0, endOffset - 1, combinedPrefix);
        }
        return this;
    }

    /**
     * Remove lines and return index from which line info must be recomputed
     *
     * @param startLine start line index to remove
     * @param endLine   end line index to remove
     * @return index from which line info must be recomputed
     */
    private int removeLinesRaw(int startLine, int endLine) {
        int useStartLine = minLimit(startLine, 0);
        int useEndLine = maxLimit(endLine, size());

        if (useStartLine < useEndLine) {
            lines.subList(useStartLine, useEndLine).clear();
            modificationCount++;

            // recompute lineInfo for lines at or after the deleted lines
            return useStartLine;
        }

        if (endLine >= size() && appendable.length() > 0) {
            // reset pending text
            resetBuilder();
        }

        return lines.size();
    }

    void recomputeLineInfo(int startLine) {
        // recompute lineInfo for lines at or after the deleted lines
        int iMax = lines.size();
        startLine = Math.max(0, startLine);

        if (startLine < iMax) {
            LineInfo lastInfo = startLine - 1 >= 0 ? lines.get(startLine - 1) : LineInfo.NULL;
            for (int i = startLine; i < iMax; i++) {
                LineInfo info = lines.get(i);
                lastInfo = LineInfo.create(lastInfo, info);
                lines.set(i, lastInfo);

                if (!lastInfo.needAggregateUpdate(info)) break;
            }
        }
    }

    @NotNull
    @Override
    public LineAppendable removeLines(int startLine, int endLine) {
        int useStartLine = removeLinesRaw(startLine, endLine);
        recomputeLineInfo(useStartLine);
        return this;
    }

    @Override
    public LineAppendable removeExtraBlankLines(int maxBlankLines, int maxTrailingBlankLines, int startLine, int endLine) {
        maxBlankLines = Math.max(0, maxBlankLines);
        maxTrailingBlankLines = Math.max(0, maxTrailingBlankLines);

        int iMax = min(endLine, size());

        int consecutiveBlankLines = 0;
        int maxConsecutiveBlankLines = maxTrailingBlankLines;
        int minRemovedLine = size();

        for (int i = iMax; i-- > 0; ) {
            LineInfo info = getLineInfo(i);

            if (info.isBlankText() && !info.isPreformatted()) {
                if (consecutiveBlankLines >= maxConsecutiveBlankLines) {
                    // remove the last blank line to stay consistent with what would be done when appendingTo
                    minRemovedLine = removeLinesRaw(i + consecutiveBlankLines, i + consecutiveBlankLines + 1);
                } else {
                    consecutiveBlankLines++;
                }
            } else {
                consecutiveBlankLines = 0;
                maxConsecutiveBlankLines = maxBlankLines;
            }
        }

        recomputeLineInfo(minRemovedLine);
        return this;
    }

    @Override
    public void setPrefixLength(int lineIndex, int prefixLength) {
        if (lineIndex == lines.size() && appendable.length() > 0) {
            line();
        }

        LineInfo info = lines.get(lineIndex);
        CharSequence line = info.line;

        if (prefixLength < 0 || prefixLength >= line.length() - 1)
            throw new IllegalArgumentException(String.format("prefixLength %d is out of valid range [0, %d) for the line", prefixLength, line.length() - 1));

        if (prefixLength != info.prefixLength) {
            CharSequence prefix = line.subSequence(0, prefixLength);
            LineInfo newInfo = LineInfo.create(
                    info.line,
                    lineIndex == 0 ? LineInfo.NULL : lines.get(lineIndex - 1),
                    prefix.length(),
                    info.prefixLength + info.textLength - prefixLength,
                    info.length,
                    isBlank(prefix),
                    isBlank(line.subSequence(prefixLength, info.getTextEnd())),
                    info.getPreformatted());

            lines.set(lineIndex, newInfo);
            this.recomputeLineInfo(lineIndex + 1);
        }
    }

    @Override
    public void setLine(int lineIndex, @NotNull CharSequence prefix, @NotNull CharSequence content) {
        if (lineIndex == lines.size() && appendable.length() > 0) {
            line();
        }

        LineInfo info = lines.get(lineIndex);
        CharSequence text = content;
        CharSequence eol = trimmedEOL(content);

        if (eol == null) eol = SequenceUtils.EOL;
        else text = text.subSequence(0, text.length() - eol.length());

        if (text.length() == 0) {
            prefix = SequenceUtils.trimEnd(prefix);
        }

        CharSequence line = appendable.getBuilder().append(prefix).append(text).append(eol).toSequence();

        LineInfo.Preformatted preformatted = info.getPreformatted();

        LineInfo newInfo = LineInfo.create(
                line,
                lineIndex == 0 ? LineInfo.NULL : lines.get(lineIndex - 1),
                prefix.length(),
                text.length(),
                line.length(),
                isBlank(prefix),
                isBlank(text),
                preformatted
        );

        lines.set(lineIndex, newInfo);
        this.recomputeLineInfo(lineIndex + 1);
    }

    int tailBlankLinesToRemove(int endLine, int maxTrailingBlankLines) {
        return max(0, trailingBlankLines(endLine) - max(0, maxTrailingBlankLines));
    }

    static class IndexedLineInfoProxy implements Indexed<LineInfo> {
        final @NotNull LineAppendableImpl appendable;
        final int startLine;
        final int endLine;
        final int maxTrailingBlankLines;

        public IndexedLineInfoProxy(@NotNull LineAppendableImpl appendable, int maxTrailingBlankLines, int startLine, int endLine) {
            this.appendable = appendable;
            this.startLine = startLine;
            this.endLine = Math.min(endLine, appendable.size());
            this.maxTrailingBlankLines = maxTrailingBlankLines;
        }

        @NotNull
        @Override
        public LineInfo get(int index) {
            if (index + startLine >= endLine)
                throw new IndexOutOfBoundsException(String.format("index %d is out of valid range [%d, %d)", index, startLine, endLine));

            return appendable.getLineInfo(index + startLine);
        }

        @Override
        public void set(int index, @NotNull LineInfo item) {
            if (index + startLine >= endLine)
                throw new IndexOutOfBoundsException(String.format("index %d is out of valid range [%d, %d)", index, startLine, endLine));

            appendable.lines.set(startLine + index, item);
            appendable.recomputeLineInfo(startLine + index + 1);
        }

        @Override
        public void removeAt(int index) {
            if (index + startLine >= endLine)
                throw new IndexOutOfBoundsException(String.format("index %d is out of valid range [%d, %d)", index, startLine, endLine));
            appendable.removeLines(index + startLine, index + 1);
        }

        @Override
        public int size() {
            int removeBlankLines = appendable.tailBlankLinesToRemove(endLine, maxTrailingBlankLines);
            return Math.max(0, endLine - startLine - removeBlankLines);
        }

        @Override
        public int modificationCount() {
            return appendable.modificationCount;
        }
    }

    static class IndexedLineProxy implements Indexed<BasedSequence> {
        final @NotNull IndexedLineInfoProxy proxy;

        public IndexedLineProxy(@NotNull IndexedLineInfoProxy proxy) {
            this.proxy = proxy;
        }

        @Override
        public BasedSequence get(int index) {
            return proxy.get(index).getLine();
        }

        @Override
        public void set(int index, BasedSequence item) {
            proxy.appendable.setLine(index + proxy.startLine, BasedSequence.NULL, item);
        }

        @Override
        public void removeAt(int index) {
            proxy.removeAt(index);
        }

        @Override
        public int size() {
            return proxy.size();
        }

        @Override
        public int modificationCount() {
            return proxy.modificationCount();
        }
    }

    @NotNull
    IndexedLineInfoProxy getIndexedLineInfoProxy(int maxTrailingBlankLines, int startLine, int endLine) {
        return new IndexedLineInfoProxy(this, maxTrailingBlankLines, startLine, endLine);
    }

    @NotNull
    IndexedLineProxy getIndexedLineProxy(int maxTrailingBlankLines, int startLine, int endLine) {
        return new IndexedLineProxy(getIndexedLineInfoProxy(maxTrailingBlankLines, startLine, endLine));
    }

    @Override
    public @NotNull Iterator<LineInfo> iterator() {
        return new IndexedItemIterator<>(getIndexedLineInfoProxy(MAX_VALUE, 0, getLineCount()));
    }

    @Override
    public @NotNull Iterable<BasedSequence> getLines(int maxTrailingBlankLines, int startLine, int endLine) {
        return new IndexedItemIterable<>(getIndexedLineProxy(maxTrailingBlankLines, startLine, endLine));
    }

    @Override
    public @NotNull Iterable<LineInfo> getLinesInfo(int maxTrailingBlankLines, int startLine, int endLine) {
        return new IndexedItemIterable<>(getIndexedLineInfoProxy(maxTrailingBlankLines, startLine, endLine));
    }
}