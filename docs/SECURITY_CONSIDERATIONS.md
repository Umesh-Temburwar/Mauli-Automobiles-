# Local Network Security Considerations

1. **Air-Gapped Operation**: No external internet connectivity or cloud server is required.
2. **Local Subnet Isolation**: HTTP requests are accepted only on local private IPv4 addresses (192.168.x.x / 10.x.x.x).
3. **Payload Sanitization**: The PC receiver strips unexpected control characters to prevent command injection.
4. **Duplicate Protection**: Unique timestamp and scan IDs prevent replay and double billing.
