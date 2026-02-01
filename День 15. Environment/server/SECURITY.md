# Security Guidelines

## Sensitive Data Management

### What is Considered Sensitive

The following data should **NEVER** be committed to version control or shared publicly:

- ✘ Server IP addresses
- ✘ Server ports (if non-standard)
- ✘ Usernames and passwords
- ✘ API keys and tokens
- ✘ Database credentials
- ✘ SSH keys
- ✘ Any authentication tokens

### Where Credentials Are Stored

**Primary Location**: `app/src/main/java/com/example/aiwithlove/util/SecureData.kt`

```kotlin
object SecureData {
    val apiKey = "YOUR_API_KEY"           // Perplexity API key
    val serverIp = "YOUR_SERVER_IP"        // Remote server IP
    val serverPort = 123                    // SSH port
    val serverLogin = "YOUR_USERNAME"       // SSH username
    val serverPassword = "YOUR_PASSWORD"    // SSH password
}
```

**⚠️ This file is already in `.gitignore`** - Do not remove it!

### Git Protection

The following `.gitignore` entry protects credentials:

**File**: `app/src/main/java/com/example/aiwithlove/util/.gitignore`
```
/SecureData.kt
/ServerConfig.kt
```

### Documentation Standards

When writing documentation (README files, comments, etc.):

✅ **DO**:
- Use placeholders: `<SERVER_IP>`, `<USERNAME>`, `<PASSWORD>`, `<API_KEY>`
- Reference SecureData.kt: "See SecureData.kt for credentials"
- Use environment variables: `$SERVER_IP`, `${SecureData.serverIp}`
- Use example values: `example.com`, `192.0.2.1` (RFC 5737)

✘ **DON'T**:
- Hardcode real IP addresses
- Include actual passwords or keys
- Share screenshots with visible credentials
- Commit debug logs with sensitive data

### Example: Safe Documentation

**Bad** ❌:
```bash
ssh root@192.0.2.1
# Password: mySecretPass123
```

**Good** ✅:
```bash
# Use credentials from SecureData.kt
ssh <USERNAME>@<SERVER_IP>
```

### Android App Configuration

**ServerConfig.kt** uses placeholders and references SecureData:

```kotlin
object ServerConfig {
    // Always use SecureData for sensitive values
    const val MCP_SERVER_URL = "http://${SecureData.serverIp}:8080"
}
```

### Before Committing

**Always check before `git commit`**:

```bash
# Check what you're about to commit
git diff --staged

# Ensure no sensitive files
git status

# Verify .gitignore is working
git check-ignore -v app/src/main/java/com/example/aiwithlove/util/SecureData.kt
```

Expected output: `SecureData.kt` should be listed as ignored.

### If Credentials Are Accidentally Committed

If you accidentally commit sensitive data:

1. **Don't just delete the file in a new commit** - it's still in git history!
2. **Rotate all compromised credentials immediately**
3. **Remove from git history**:
   ```bash
   git filter-branch --force --index-filter \
     "git rm --cached --ignore-unmatch path/to/SecureData.kt" \
     --prune-empty --tag-name-filter cat -- --all
   ```
4. **Force push** (if already pushed):
   ```bash
   git push origin --force --all
   ```

### Environment Variables (Alternative)

For CI/CD or production deployment, consider using environment variables:

```kotlin
object SecureData {
    val apiKey = System.getenv("PERPLEXITY_API_KEY") ?: "default_key"
    val serverIp = System.getenv("SERVER_IP") ?: "localhost"
    val serverLogin = System.getenv("SERVER_LOGIN") ?: "user"
    val serverPassword = System.getenv("SERVER_PASSWORD") ?: ""
}
```

### Security Checklist

Before sharing code or documentation:

- [ ] No IP addresses in code (use placeholders)
- [ ] No passwords in code (use SecureData.kt)
- [ ] No API keys in code (use SecureData.kt)
- [ ] SecureData.kt is in .gitignore
- [ ] READMEs use placeholders, not real values
- [ ] No screenshots with visible credentials
- [ ] No debug logs with sensitive data

### Additional Resources

- [OWASP: Storing Secrets](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html)
- [GitHub: Removing Sensitive Data](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository)
- [Android: Security Best Practices](https://developer.android.com/privacy-and-security/security-tips)

## Questions?

If unsure whether something is sensitive, **err on the side of caution** and treat it as sensitive.
