# Contributing to SS7 HA Gateway

First off, thank you for considering contributing to SS7 HA Gateway! It's people like you that make this project a great tool for the telecom community.

## Code of Conduct

By participating in this project, you are expected to uphold our Code of Conduct:

- Be respectful and inclusive
- Welcome newcomers and encourage diverse contributions
- Focus on what is best for the community
- Show empathy towards other community members

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check the existing issues to avoid duplicates. When creating a bug report, include as many details as possible:

- **Use a clear and descriptive title**
- **Describe the exact steps to reproduce the problem**
- **Provide specific examples** (configuration files, code snippets)
- **Describe the behavior you observed** and what you expected
- **Include logs and error messages**
- **Specify your environment**: Java version, OS, Redis/Kafka versions

**Bug Report Template:**
```markdown
### Description
[Clear description of the bug]

### Steps to Reproduce
1. 
2. 
3. 

### Expected Behavior
[What you expected to happen]

### Actual Behavior
[What actually happened]

### Environment
- Java Version:
- OS:
- SS7 HA Gateway Version:
- Redis Version:
- Kafka Version:

### Logs
```
[Paste relevant logs here]
```
```

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion:

- **Use a clear and descriptive title**
- **Provide a detailed description** of the suggested enhancement
- **Explain why this enhancement would be useful** to most users
- **List any alternative solutions** you've considered

### Pull Requests

We actively welcome your pull requests:

1. **Fork the repo** and create your branch from `main`
2. **Add tests** if you've added code that should be tested
3. **Ensure the test suite passes**: `mvn clean test`
4. **Make sure your code follows** our coding style (see below)
5. **Write a good commit message** (see below)
6. **Update documentation** if needed
7. **Submit the pull request**

## Development Process

### Setting Up Development Environment

```bash
# Clone your fork
git clone https://github.com/YOUR_USERNAME/ss7-ha-gateway.git
cd ss7-ha-gateway

# Add upstream remote
git remote add upstream https://github.com/3eAI-labs/ss7-ha-gateway.git

# Create a branch
git checkout -b feature/your-feature-name

# Install dependencies and build
mvn clean install
```

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=DialogStateConverterTest

# Run with coverage
mvn clean test jacoco:report
```

### Coding Style

- Follow standard Java conventions
- Use **4 spaces** for indentation (not tabs)
- Maximum line length: **120 characters**
- Use **meaningful variable names**
- Add **JavaDoc** for public methods and classes
- Keep methods **focused and small** (< 50 lines ideally)

**Example:**
```java
/**
 * Converts a JSS7 Dialog to a primitive DialogState object.
 * This ensures no SS7 stack objects leak across architectural boundaries.
 *
 * @param dialog the JSS7 Dialog to convert
 * @return DialogState containing only primitive types
 * @throws IllegalArgumentException if dialog is null
 */
public static DialogState fromDialog(Dialog dialog) {
    if (dialog == null) {
        throw new IllegalArgumentException("Dialog cannot be null");
    }
    
    DialogState state = new DialogState();
    state.setDialogId(dialog.getLocalDialogId());
    // ...
    return state;
}
```

### Commit Message Guidelines

We follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:**
- `feat`: A new feature
- `fix`: A bug fix
- `docs`: Documentation only changes
- `style`: Code style changes (formatting, missing semicolons, etc.)
- `refactor`: Code change that neither fixes a bug nor adds a feature
- `perf`: Performance improvement
- `test`: Adding or updating tests
- `chore`: Changes to build process or auxiliary tools

**Examples:**
```
feat(kafka): add support for Avro message serialization

Add Avro schema registry integration for Kafka messages.
This enables schema evolution and better data governance.

Closes #123
```

```
fix(redis): prevent connection leak on cluster failover

RedisDialogStore was not properly closing connections during
cluster topology changes, causing connection pool exhaustion.

Fixes #456
```

### Testing Guidelines

- **Write unit tests** for new features
- **Maintain test coverage** above 80%
- **Use meaningful test names**: `testMethodName_Scenario_ExpectedResult()`
- **Follow AAA pattern**: Arrange, Act, Assert

**Example:**
```java
@Test
public void testFromDialog_WithNullDialog_ThrowsException() {
    // Arrange
    Dialog nullDialog = null;
    
    // Act & Assert
    assertThrows(IllegalArgumentException.class, () -> {
        DialogStateConverter.fromDialog(nullDialog);
    });
}
```

## Project Structure

```
ss7-ha-gateway/
├── ss7-core/              # Core SS7 stack integration
│   ├── src/main/java/     # Main source code
│   └── src/test/java/     # Unit tests
├── ss7-kafka-bridge/      # Kafka integration layer
├── ss7-ha-manager/        # HA coordination
├── docs/                  # Documentation
├── docker/                # Docker configurations
└── k8s/                   # Kubernetes manifests
```

## Release Process

(Maintainers only)

1. Update version in `pom.xml` files
2. Update `CHANGELOG.md`
3. Create release tag: `git tag -a v1.0.0 -m "Release 1.0.0"`
4. Push tag: `git push origin v1.0.0`
5. Create GitHub release with release notes
6. Publish to Maven Central (if applicable)

## Getting Help

- **Documentation**: Check the [docs/](docs/) directory
- **GitHub Issues**: Search existing issues or create new one
- **Discussions**: Use [GitHub Discussions](https://github.com/3eAI-labs/ss7-ha-gateway/discussions)

## License

By contributing, you agree that your contributions will be licensed under the AGPL-3.0 License.

---

Thank you for contributing to SS7 HA Gateway! 🎉
