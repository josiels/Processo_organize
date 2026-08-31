package com.josiel.organizeprocesso.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.josiel.organizeprocesso.domain.model.StatusSemaforo
import com.josiel.organizeprocesso.ui.theme.Indigo600
import com.josiel.organizeprocesso.ui.theme.IndigoPastel
import com.josiel.organizeprocesso.ui.theme.Organize_ProcessoTheme
import com.josiel.organizeprocesso.ui.theme.VermelhoCritico

/**
 * Vitrine dos componentes base do design system (ROADMAP.md, passo 2).
 * Sem lógica de navegação/dados — só para conferir o tema no Preview do
 * Android Studio antes de ligar as telas reais.
 */
@Composable
private fun ComponentsShowcase(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Organize_Processo", style = MaterialTheme.typography.headlineSmall)

            SideBarCard(barColor = VermelhoCritico) {
                IconCircle(backgroundColor = IndigoPastel) {
                    Icon(imageVector = Icons.Filled.Person, contentDescription = null, tint = Indigo600)
                }
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                    Text("Processo 001/2026", style = MaterialTheme.typography.titleSmall)
                    Text("Aquisição de licenças de software", style = MaterialTheme.typography.bodySmall)
                }
                SemaforoPill(status = StatusSemaforo.CRITICO)
                NavigationChevron(modifier = Modifier.padding(start = 8.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SemaforoPill(status = StatusSemaforo.OK)
                SemaforoPill(status = StatusSemaforo.ATENCAO)
                SemaforoPill(status = StatusSemaforo.CRITICO)
            }

            PillButton(text = "Ver todos os processos", onClick = {})

            var notificar by remember { mutableStateOf(true) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Notificar sobre prazo desta fase?",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                AppToggle(checked = notificar, onCheckedChange = { notificar = it })
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ComponentsShowcasePreview() {
    Organize_ProcessoTheme {
        ComponentsShowcase()
    }
}
