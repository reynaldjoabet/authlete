
### Parse Input
The generator reads some structured input format. Common examples:
OpenAPI YAML/JSON spec
Protobuf schema
GraphQL schema
Database schema
UML or metadata definitions
The generator converts this into an internal representation (AST / object graph).

### Map Input → Intermediate Model
The generator translates the parsed spec into a language-agnostic internal model:
For example, for OpenAPI:
Models → lists of properties with types, nullability, required fields
Endpoints → HTTP method, path, parameters, request bodies
Enums → enum values
Metadata → description, tags, security
This model is not yet Scala/Java/Python/etc.
Just structural information.
This stage ensures the generator can target different languages without repeating parsing logic.


### Apply Templates
A code generator does not write code directly.
Instead, it uses templates—usually Mustache, Handlebars, or similar.
Example:
- `model.mustache` → generates a Scala case class for each schema
- `api.mustache` → generates a client for each endpoint
- `enum.mustache` → generates a sealed trait or enum
The generator:
Loops over internal models
Applies template logic
Fills in placeholders with actual data
Produces the corresponding files

### Write Files
The generator writes the resulting files to a directory.
Key behaviors:
Can output to:
- src_managed (recommended)
- src/main (not recommended)
- A standalone directory (CLI mode)

you can override the templates that produce method names:
- Create a template folder (e.g. project/openapi-templates/scala).
- Copy the relevant template (API template, e.g. `api.mustache` or function naming logic) from the generator’s repo, remove the Api suffix in the template logic.
- Point sbt at your template dir:

`openApiTemplateDir := baseDirectory.value / "project" / "openapi-templates" / "scala"`