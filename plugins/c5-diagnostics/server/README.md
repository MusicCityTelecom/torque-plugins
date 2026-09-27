# Server2 webhook receiver

receiver.php is an optional receiver for off-site C5 diagnostic reports.

## Recommended deployment

Place receiver.php in a dedicated HTTPS virtual host or protected application directory. Store reports outside the web document root.

Example environment variables:

    TORQUE_WEBHOOK_SECRET=replace-with-a-long-random-secret
    TORQUE_REPORT_DIR=/var/lib/torque-reports

The web-server/PHP-FPM user must be able to write TORQUE_REPORT_DIR.

Do not put the shared secret in the repository.

## Apache example

Expose a URL such as:

    https://diag.example.com/torque/report

Route it to receiver.php using your normal Apache/PHP-FPM configuration. TLS is required by the Android client.

## Security behavior

The receiver:

- accepts POST only
- limits the request body to 8 MiB
- requires an integer timestamp within 5 minutes of server time
- verifies HMAC-SHA256 over timestamp + "." + exact raw request body
- rejects invalid JSON
- sanitizes the session ID before using it in a filename
- writes with LOCK_EX
- does not execute data from the report

For a public endpoint, also apply ordinary web-server rate limiting and logging.
