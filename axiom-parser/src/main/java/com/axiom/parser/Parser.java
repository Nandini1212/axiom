package com.axiom.parser;

import com.axiom.common.model.FailureEvent;

import java.io.InputStream;

/**
 * Converts one test report document into {@link FailureEvent}s. Does not classify, enrich with
 * pipeline/CI context, or acquire the input itself (the caller owns fetching and closing the
 * stream) — this is purely "report bytes in, normalized failures out."
 */
public interface Parser {

    /**
     * @param input the report document; not closed by this method
     * @return every non-passing test case as a {@link FailureEvent}, plus any recoverable
     *         problems encountered along the way
     * @throws ParsingException if the document itself cannot be parsed as valid XML
     */
    ParserResult parse(InputStream input);
}
