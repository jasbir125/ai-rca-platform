{{/* Call with (dict "svc" $svcValues "root" $) */}}
{{- define "rca-platform.image" -}}
{{- if .root.Values.image.registry -}}
{{ .root.Values.image.registry }}/{{ .svc.image }}:{{ .root.Values.image.tag }}
{{- else -}}
{{ .svc.image }}:{{ .root.Values.image.tag }}
{{- end -}}
{{- end -}}

{{- define "rca-platform.labels" -}}
app.kubernetes.io/name: {{ . }}
app.kubernetes.io/part-of: rca-platform
{{- end -}}
