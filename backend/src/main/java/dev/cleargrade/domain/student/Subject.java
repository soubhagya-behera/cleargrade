package dev.cleargrade.domain.student;

/** The five required subjects; column names match the documented data model. */
public enum Subject {

	MATHS("maths"),
	SCIENCE("science"),
	ENGLISH("english"),
	SOCIAL_STUDIES("social_studies"),
	SECOND_LANGUAGE("second_language");

	private final String columnName;

	Subject(String columnName) {
		this.columnName = columnName;
	}

	public String columnName() {
		return this.columnName;
	}
}
