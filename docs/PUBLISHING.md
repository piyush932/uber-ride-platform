# Publishing checklist

Keep automated tests and smoke scripts. Review current files and Git history for .env files, real credentials, database dumps, logs and tokens. Rotate any real secret that was committed; deleting the latest copy does not remove history.

The repository was private when inspected. After review, use GitHub Settings > General > Danger Zone > Change repository visibility > Public and follow its confirmation prompts. This package does not change visibility or deploy the running application publicly.

Suggested description: Full-stack ride-booking application with Spring Boot microservices, React portals, Redis GEO matching, timed dispatch and Docker Compose.

Suggested topics: java, spring-boot, microservices, react, mysql, redis, docker-compose, ride-booking.

Add screenshots and a short recording from an actual run. Describe implementation and trade-offs plainly. Do not claim payments, admin workflows, performance metrics, production guarantees or tested Kafka consumers that are not implemented/verified. Container runtime verification remains pending.
