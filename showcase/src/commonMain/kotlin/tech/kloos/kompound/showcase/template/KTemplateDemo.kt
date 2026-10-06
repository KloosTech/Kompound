package tech.kloos.kompound.showcase.template

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.json.JsonFields
import tech.kloos.kompound.json.JsonValue
import tech.kloos.kompound.json.Template
import tech.kloos.kompound.json.TemplateException
import tech.kloos.kompound.json.TemplateScope
import tech.kloos.kompound.json.parseOrNull
import tech.kloos.kompound.template.FieldBinding
import tech.kloos.kompound.template.KFieldMapper
import tech.kloos.kompound.template.KFieldPicker
import tech.kloos.kompound.template.KTemplateArea
import tech.kloos.kompound.template.KTemplateField
import tech.kloos.kompound.template.ParamSpec
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.KTextArea

private const val Usage_template = """import tech.kloos.kompound.json.*
import tech.kloos.kompound.template.*

val fields = JsonFields.of(sample)                    // or engine.fieldsOf(node, "in") in a graph

KTemplateField(url, { url = it }, fields, label = "URL")          // type {{ to pick a field; unknown ones are marked
KTemplateArea(body, { body = it }, fields, label = "Body")
KFieldPicker(fields, path, { path = it }, label = "Sort by")
KFieldMapper(fields, listOf(ParamSpec("b", "Second number", "number")), bindings, { bindings = it })

// At run time:
val text = Template.render(body, TemplateScope.of(sample))        // {{title}}, {{owner.name}}, {{tasks[*].title|json}}
val value = bindings["b"]?.resolve(TemplateScope.of(sample))      // a field keeps its JSON type
// In a graph node: Content { TemplateField(engine, body, { body = it }, from = "in") } and ctx.render(body) in the runner."""

private const val SampleJson = """{
  "title": "Fix the login",
  "description": "Users cannot sign in",
  "priority": 2,
  "owner": { "name": "Ann Lee", "email": "ann@example.com" },
  "tasks": [ { "title": "Reproduce", "done": true }, { "title": "Patch", "done": false } ]
}"""

@KompoundDemo(
    id = "template.fields",
    title = "Templates and field pickers",
    description = "Text with {{placeholders}} that offers the fields of your data as you type, marks unknown ones, plus a field picker and a parameter mapper.",
    category = KompoundCategory.Inputs,
    tags = ["template", "placeholder", "json", "field", "autocomplete", "mapper", "binding"],
    since = "0.2.0",
    status = "Experimental",
    usage = Usage_template,
)
@Composable
fun DemoScope.KTemplateDemo() {
    val known = boolControl("Fields known", true)
    var json by remember { mutableStateOf(SampleJson) }
    var url by remember { mutableStateOf("https://tracker.example/api/issues/{{priority}}?q={{title|url}}") }
    var body by remember { mutableStateOf("{\n  \"name\": \"{{title|json}}\",\n  \"owner\": \"{{owner.name}}\",\n  \"first\": \"{{tasks[0].title}}\"\n}") }
    var path by remember { mutableStateOf("owner.email") }
    var bindings by remember { mutableStateOf<Map<String, FieldBinding>>(mapOf("b" to FieldBinding.Field("priority"))) }
    val parsed = remember(json) { JsonValue.parseOrNull(json) }
    val fields = if (known && parsed != null) JsonFields.of(parsed) else emptyList()
    val scope = parsed?.let { TemplateScope.of(it) }
    fun rendered(text: String): String = try { scope?.let { Template.render(text, it) } ?: "(fix the JSON)" } catch (e: TemplateException) { "error: ${e.message}" }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        KTextArea(json, { json = it }, Modifier.fillMaxWidth(), label = "Data (what the previous node produced)", minLines = 6, maxLines = 10, isError = parsed == null, supportingText = if (parsed == null) "Not valid JSON" else null)
        KTemplateField(url, { url = it }, fields, Modifier.fillMaxWidth(), label = "URL", supportingText = "Type {{ to pick a field")
        KText("→ ${rendered(url)}")
        KTemplateArea(body, { body = it }, fields, Modifier.fillMaxWidth(), label = "Body")
        KText("→ ${rendered(body)}")
        KFieldPicker(fields, path, { path = it }, Modifier.fillMaxWidth(), label = "Sort by (one path)")
        KFieldMapper(fields, listOf(ParamSpec("a", "First number", "number"), ParamSpec("b", "Second number", "number")), bindings, { bindings = it })
        for ((key, b) in bindings) KText("$key = ${try { scope?.let { b.resolve(it)?.toJson() } } catch (e: TemplateException) { e.message }}")
    }
}
