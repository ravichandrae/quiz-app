{{- define "quiz.fullname" -}}
{{- printf "%s" .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "quiz.labels" -}}
app.kubernetes.io/name: schoolmela-quiz
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
{{- end -}}

{{- define "quiz.selector" -}}
app.kubernetes.io/name: schoolmela-quiz
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "quiz.image" -}}
{{- $root := index . 0 -}}{{- $name := index . 1 -}}
{{- printf "%s/%s:%s" $root.Values.image.registry $name (default $root.Chart.AppVersion $root.Values.image.tag) -}}
{{- end -}}

{{- define "quiz.secretName" -}}
{{- default (printf "%s-secrets" (include "quiz.fullname" .)) .Values.secrets.existingSecret -}}
{{- end -}}

{{/* Locked-down container settings shared by every container. */}}
{{- define "quiz.containerSecurity" -}}
allowPrivilegeEscalation: false
readOnlyRootFilesystem: true
capabilities:
  drop: ["ALL"]
{{- end -}}

{{- define "quiz.podSecurity" -}}
runAsNonRoot: true
seccompProfile:
  type: RuntimeDefault
{{- end -}}
