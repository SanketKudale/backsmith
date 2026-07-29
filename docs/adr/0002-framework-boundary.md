# ADR 0002: Framework adapter boundary

Status: accepted.

Framework-specific generation belongs in separate adapter modules. Core and CLI code depend only on adapter contracts and immutable configuration. This prevents Spring concepts from becoming assumptions that block future framework implementations.
