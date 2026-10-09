package dev.lowball.helper.config;

public enum RoundMode {
	NONE("Exact", 0),
	SIG2("2 digits (8.7M)", 2),
	SIG3("3 digits (8.73M)", 3);

	public final String label;
	public final int sig;

	RoundMode(String label, int sig) {
		this.label = label;
		this.sig = sig;
	}
}
