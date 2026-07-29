# Security model

The generator treats the destination tree as a trust boundary. All adapter paths must remain below the normalized project root. Different existing content is a conflict unless the caller explicitly selects force; temporary same-directory files limit partial replacement risk.

Backsmith never generates real secrets. Generated `.env.example` values are local placeholders. The 0.1.0 Spring output does not yet implement authentication or authorization; selecting a security label records intent but must not be mistaken for runtime protection.
