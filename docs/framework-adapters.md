# Framework adapters

Implement `BackendFrameworkAdapter` and provide a stable ID, capability set, and deterministic mapping of relative paths to rendered content.

Adapters must not write files. They must return only relative paths, avoid timestamps and random data in rendered sources, and represent unsupported combinations as validation errors. The CLI selects an adapter; the core owns planning and application.

Future Kotlin/Spring, NestJS, ASP.NET Core, Go, and FastAPI support should live in independent modules. No placeholder adapter should be registered before it can generate and verify a working project.
