package com.example.yugiohscanner.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yugiohscanner.ui.theme.FondoGradiente
import com.example.yugiohscanner.ui.theme.IconoCamara
import com.example.yugiohscanner.ui.theme.OroClaro
import com.example.yugiohscanner.ui.theme.OroYuGiOh
import com.example.yugiohscanner.ui.theme.TextoSecundario

/**
 * Pantalla de bienvenida que se muestra UNA sola vez, en el primer arranque (ver [PreferenciasApp]).
 * No hay login como puerta: la app es offline y el inicio de sesión es opcional (Ajustes), así que
 * aquí solo damos la bienvenida y llevamos al usuario al escáner.
 *
 * @param onEmpezar se llama al pulsar cualquier acción; marca la bienvenida como vista y entra a la app.
 */
@Composable
fun BienvenidaScreen(onEmpezar: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FondoGradiente)
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Emblema
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(2.dp, OroYuGiOh)
            ) {
                Text(
                    text = "🃏",
                    fontSize = 52.sp,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "¡Bienvenido, duelista!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = OroClaro,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Tu álbum de cartas Yu-Gi-Oh!: escanéalas, colecciónalas y arma tus mazos. Todo funciona sin conexión.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Acción principal
            Button(
                onClick = onEmpezar,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OroYuGiOh,
                    contentColor = Color.Black
                )
            ) {
                Icon(IconoCamara, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Escanear mi primera carta", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(6.dp))

            TextButton(onClick = onEmpezar, modifier = Modifier.fillMaxWidth()) {
                Text("Entrar sin escanear", color = TextoSecundario)
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Consejo del escáner (el dato clave del passcode).
            TarjetaConsejo()

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No necesitas cuenta. El inicio de sesión es opcional (Ajustes) y solo sirve para copias de seguridad.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun TarjetaConsejo() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .background(OroYuGiOh, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "💡 Consejo",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = OroClaro
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Apunta a la esquina inferior izquierda de la carta para leer su código de 8 dígitos (passcode). Es la forma más fiable de identificarla.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
