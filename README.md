# Exorcist

Summon the ghosts of legacy Java code.

Exorcist is a terminal app that reads a Git repository, finds who wrote each Java class, and lets the
**ghost of its main author** explain the class in first person, confess its sins and propose a refactoring.
Everything runs locally: the "ghost" is an open-weight model served by [Ollama](https://ollama.com), so your code
never leaves your machine.

```
     .-.
    (o o)   E X O R C I S T
    | O \   summon the ghosts of legacy Java code
     \   \  model: qwen2.5-coder:7b, running locally via Ollama
      `~~~'
```

The tone is theatrical, but the facts are not. Every claim the ghost makes must cite line numbers, its life story
comes only from real commits, and refactorings are shown as a diff. Exorcist never writes to your files.

## Requirements

- **JDK 21** or newer
- **Ollama** running locally, with a model pulled. The default is `qwen2.5-coder:7b` (about 4.7 GB):

  ```sh
  ollama pull qwen2.5-coder:7b
  ollama serve        # if it is not already running as a service
  ```

- A **Git repository** with Java code. The more history it has, the better the ghosts.

Maven comes with the project (`./mvnw`), so you don't need to install it.

## Getting started

```sh
./mvnw package
java -jar target/exorcist-0.1.0-SNAPSHOT.jar
```

On Windows use `mvnw.cmd package`. Then, in the shell:

```
exorcist:> summon ../some-legacy-project
exorcist:> possess Assert
exorcist[Assert]:> confess
exorcist[Assert]:> exorcise
exorcist[Assert]:> epitaph
```

`help` lists every command, and `help <command>` explains one. Tab completes commands and options.

## Commands

| Command | What it does |
|---|---|
| `summon <path> [--limit 50]` | Scans a Git repository (or a folder inside one) and lists its Java classes with their main author, first and last commit, longest dead first. `--limit 0` shows all of them. |
| `possess <class>` | The ghost of the class's main author explains it in first person: who they were, when the file was born, and what each important member does, with line numbers. Accepts `Assert`, `org.junit.Assert` or a path. |
| `confess` | The possessed ghost confesses the class's code smells as a numbered list, worst first, each with its lines. |
| `exorcise [--lines 40-52]` | The ghost rewrites the code holding its worst confirmed sin and shows the change as a unified diff. `--lines` picks the lines yourself. **Nothing is written to disk.** |
| `epitaph [class]` | Carves an ASCII tombstone for the possessed class (or the one named) with a one-line epitaph. |
| `seance [message]` | Sends a single message to the model, to check that Ollama answers. |

A possessed class shows up in the prompt (`exorcist[Assert]:>`) until you possess another one or summon again.

### Example

`summon` and `epitaph`, run on this repository:

```
exorcist:> summon . --limit 3
Opening the crypt and reading its history...
27 Java spirits rest in C:\Projects\Exorcist, longest dead first.

Class              Spirit  Born        Died        Commits
-----------------  ------  ----------  ----------  -------
ExorcistException  mathfe  2026-10-27  2026-10-27        1
AuthorIdentities   mathfe  2026-10-27  2026-10-27        1
CommitInfo         mathfe  2026-10-27  2026-10-27        1

... and 24 more restless spirits (use --limit 0 to see them all).

exorcist:> epitaph CodeParserService
Carving the stone...

        ________________________________
      .'                                '.
     /                                    \
    |              R . I . P               |
    |                                      |
    |          CodeParserService           |
    |          dev.exorcist.code           |
    |                                      |
    |              haunted by              |
    |                mathfe                |
    |                                      |
    |      2026-10-27  -  2026-10-28       |
    |                                      |
    |   "Finds a class in a Java source    |
    |   file and maps out its members by   |
    |            line number."             |
    |                                      |
    |        3 commits | 0 mourners        |
~~~~|______________________________________|~~~~
```

When the model is unavailable, the epitaph falls back to the first sentence of the class Javadoc, as it does here.

### Applying an exorcism

`exorcise` prints a diff like this one, which casts out a swallowed exception:

```diff
--- a/src/main/java/org/example/Counter.java
+++ b/src/main/java/org/example/Counter.java
@@ -17,7 +17,8 @@
     public void run() {
         try {
             count++;
-        } catch (Exception e) {
+        } catch (IllegalStateException e) {
+            throw e;
         }
     }
 
```

Review it. If you like it, save it to a file and apply it yourself with `git apply exorcism.diff`. Exorcist warns you
when the change only moves whitespace around or leaves the file unparseable.

## How it keeps a small model honest

A 7B model is happy to make things up, so Exorcist hands it facts and checks what comes back:

- **Ghosts come from Git, not from the model.** One walk over the history (merges skipped, renames followed) finds
  who touched each file. People with several names and e-mails over the years are merged into one, without merging
  strangers who share an address like `noreply@github.com`. `git blame` says which lines other hands wrote last.
- **No line number, no claim.** The source goes to the model with line numbers, and every answer is checked: you see
  how many lines it cited, which citations point outside the file, and which confessions cite nothing.
- **Sins are found before they are confessed.** A static scan with JavaParser finds empty or overly broad `catch`
  blocks, `printStackTrace()`, `System.out`, deep nesting, long methods and parameter lists, public mutable fields,
  `Vector`/`Hashtable`, `@Deprecated` code and TODOs, each with its exact lines. The model gets these as evidence.
- **The model only writes code. Exorcist computes the diff.** `exorcise` targets a sin the scan confirms, widened to
  the smallest complete code that fixes it (the whole `try` statement, the statement, the method). The model returns
  `REPLACE LINES a-b` and a code block. Exorcist splices it in, re-indents it, checks that the file still parses
  and computes the diff with JGit, so the diff is always well-formed.
- **Prompts fit the model's memory.** Long files are cut to a window around the lines that matter, and the model is
  told which parts it cannot see.

## Configuration

Settings live in [`src/main/resources/application.yml`](src/main/resources/application.yml). The common ones can be
set through environment variables:

| Variable | Default | |
|---|---|---|
| `EXORCIST_MODEL` | `qwen2.5-coder:7b` | Any model from `ollama list`, e.g. `llama3.1:8b` |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Where Ollama is listening |
| `NO_COLOR` | unset | Set to anything to turn colours off |

Prompt size limits, for smaller or larger models: `exorcist.max-source-lines` (500), `exorcist.max-commit-messages`
(15) and `exorcist.max-outline-members` (80). Override any setting with `-D`, before `-jar`. Arguments after the
jar are read as shell commands, so `--some.property` does not work there:

```sh
java -Dexorcist.max-source-lines=300 -jar target/exorcist-0.1.0-SNAPSHOT.jar
```

To see the exact prompts sent to the model, add `-Dlogging.level.dev.exorcist.ai=debug`.

### Scripted runs

Commands passed as arguments run without the interactive shell. A file of commands runs with `script`:

```sh
java -jar target/exorcist-0.1.0-SNAPSHOT.jar summon ../junit4 --limit 0
java -jar target/exorcist-0.1.0-SNAPSHOT.jar script seance.txt
```

## Development

```sh
./mvnw test
```

The tests need neither Ollama nor network access. They cover:

- **Git**: real repositories built commit by commit in a temporary folder, with renames, merges, aliases, blame,
  Latin-1 files and the error cases (no commits, no Java, not a repository).
- **Code**: parsing of members and line ranges, every smell the scanner knows, diffs that `git apply` really accepts.
- **AI**: the prompts (facts, line numbering, cut windows, pinned exorcism lines), parsing of the model's replies,
  citation checks, target selection, and translation of Ollama errors into friendly messages (with a mocked
  `ChatClient`).

The project layout follows the same split:

```
dev.exorcist
├── git     history, identities and blame (JGit)
├── code    parsing, smell scan and diffs (JavaParser, JGit)
├── ai      prompts and the only class that talks to the model (Spring AI)
└── shell   commands, session state and rendering (Spring Shell, JLine)
```

Built with Java 21, Spring Boot 3.5, Spring Shell 3.4, Spring AI 1.1 (Ollama), JGit and JavaParser.
