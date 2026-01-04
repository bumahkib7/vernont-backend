# Argon2 Password Security Implementation

## Why Argon2 Over BCrypt?

### Security Advantages

1. **Memory-Hard Function**: Argon2 requires significant memory, making it resistant to:
   - ASIC (Application-Specific Integrated Circuit) attacks
   - GPU-based parallel attacks
   - Custom hardware attacks

2. **Future-Proof Design**: 
   - Winner of the Password Hashing Competition (PHC)
   - Designed with modern attack vectors in mind
   - Configurable parameters allow adaptation to future hardware

3. **Side-Channel Resistance**: Argon2id variant provides:
   - Data-independent memory access patterns (Argon2i)
   - Data-dependent memory access patterns (Argon2d)
   - Best of both approaches

### Performance Comparison

| Algorithm | Time | Memory | GPU Resistance | ASIC Resistance |
|-----------|------|--------|----------------|-----------------|
| BCrypt    | ✓    | Low    | Moderate       | Low            |
| Argon2    | ✓    | High   | High          | High           |

## Configuration

### Default Parameters (OWASP Recommended)

```yaml
nexus:
  security:
    argon2:
      iterations: 3      # Time cost
      memory: 65536      # 64MB memory cost  
      parallelism: 1     # Thread count
```

### Parameter Tuning

**Time Cost (iterations)**:
- Minimum: 3
- Recommended: 3-5
- Higher = slower hashing but more secure

**Memory Cost**:
- Minimum: 64MB (65536 KB)
- Recommended: 64-128MB for web applications
- Higher = more memory required but better GPU resistance

**Parallelism**:
- Recommended: 1 for most web applications
- Higher values can utilize multiple cores but may not improve security significantly

### Environment Variables

```bash
# Production settings
export ARGON2_ITERATIONS=4
export ARGON2_MEMORY=131072  # 128MB
export ARGON2_PARALLELISM=1
```

## Security Features

### Automatic Hash Upgrading

The implementation includes automatic password hash upgrading:

```kotlin
// During login, check if password needs stronger hashing
val upgradedHash = passwordEncoder.upgradePassword(rawPassword, currentHash)
if (upgradedHash != null) {
    // Save new hash to database
    userService.updatePasswordHash(userId, upgradedHash)
}
```

### Memory Security

- Passwords are cleared from memory immediately after use
- Character arrays are wiped using `argon2.wipeArray()`
- Reduces risk of memory dumps exposing passwords

### Hash Format

Argon2 produces self-describing hashes:
```
$argon2id$v=19$m=65536,t=3,p=1$saltBase64$hashBase64
```

This allows:
- Parameter extraction for upgrade decisions
- Version tracking for migration
- Algorithm verification

## Migration from BCrypt

### Gradual Migration Strategy

1. **Dual Support Phase**:
   ```kotlin
   fun verifyPassword(raw: String, hash: String): Boolean {
       return when {
           hash.startsWith("$argon2") -> argon2Encoder.matches(raw, hash)
           hash.startsWith("$2a$") || hash.startsWith("$2b$") -> bcryptEncoder.matches(raw, hash)
           else -> false
       }
   }
   ```

2. **Upgrade on Login**:
   ```kotlin
   if (passwordMatches && hash.startsWith("$2")) {
       // BCrypt hash - upgrade to Argon2
       val newHash = argon2Encoder.encode(rawPassword)
       userService.updatePasswordHash(userId, newHash)
   }
   ```

3. **Force Migration**: After sufficient time, require password reset for remaining BCrypt hashes.

## Production Considerations

### Hardware Requirements

- **Memory**: Ensure sufficient RAM for concurrent password operations
- **CPU**: Argon2 is CPU-intensive but typically faster than BCrypt at equivalent security
- **Monitoring**: Track hashing performance and adjust parameters if needed

### Load Testing

Test with expected concurrent login load:
```bash
# Example: 100 concurrent logins
wrk -t12 -c100 -d30s --script=login_test.lua http://localhost:8080/store/auth/login
```

### Security Monitoring

Monitor for:
- Unusual password hashing latency (possible DoS attack)
- Memory usage spikes
- Failed login patterns

## Compliance

This implementation meets security standards for:
- **OWASP Application Security Verification Standard (ASVS)**
- **NIST SP 800-63B** password guidelines
- **PCI DSS** requirements for password protection
- **GDPR** data protection requirements

## Testing

```kotlin
@Test
fun `argon2 password encoding and verification`() {
    val encoder = Argon2PasswordEncoder()
    val password = "SecureP@ssw0rd123!"
    
    val hash = encoder.encode(password)
    assertTrue(encoder.matches(password, hash))
    assertFalse(encoder.matches("WrongPassword", hash))
}

@Test  
fun `password upgrade detection`() {
    val encoder = Argon2PasswordEncoder()
    val weakHash = "$argon2id$v=19$m=4096,t=1,p=1$..."
    
    assertTrue(encoder.needsRehashing(weakHash))
}
```