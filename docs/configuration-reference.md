# Configuration reference

Schema version 1 has five sections: `project`, `runtime`, `architecture`, `features`, and `modules`. See `backsmith.example.yaml`.

Project names use letters, digits, and hyphens and must start with a letter. Base packages must be valid dotted Java packages. Java 21, Spring, Maven, and PostgreSQL are the supported initial runtime. Unknown future fields are not yet preserved during rewrite.
