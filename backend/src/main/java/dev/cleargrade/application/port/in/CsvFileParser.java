package dev.cleargrade.application.port.in;

import dev.cleargrade.application.model.ParsedCsv;

/** Parses uploaded bytes into header-mapped rows without applying business validation. */
public interface CsvFileParser {

	ParsedCsv parse(byte[] content);
}
