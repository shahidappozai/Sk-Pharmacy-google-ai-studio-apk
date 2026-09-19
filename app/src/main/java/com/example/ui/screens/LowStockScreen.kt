package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ProductWithStock
import com.example.ui.MainViewModel
import com.example.ui.components.PharmacyHeader
import com.example.ui.components.StatusBadge
import com.example.ui.theme.PharmacyTealPrimary
import com.example.util.PdfGenerator
import com.example.util.PrintUtil

@Composable
fun LowStockScreen(
    viewModel: MainViewModel,
    onNavigateToPurchases: () -> Unit
) {
    val context = LocalContext.current
    val products by viewModel.productsWithStock.collectAsState()
    val settings by viewModel.pharmacySettings.collectAsState()

    val lowStockProducts = products.filter { it.totalStock <= it.minStockLevel }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        PharmacyHeader(
            title = "Low Stock & Reorders",
            subtitle = "${lowStockProducts.size} medicines below minimum threshold"
        ) {
            if (lowStockProducts.isNotEmpty()) {
                IconButton(onClick = {
                    val pdf = PdfGenerator.generateReorderReportPdf(context, lowStockProducts, settings)
                    PrintUtil.sharePdf(context, pdf, "SK Pharmacy Reorder Report")
                }) {
                    Icon(Icons.Default.Share, contentDescription = "Share PDF", tint = PharmacyTealPrimary)
                }
            }
        }

        if (lowStockProducts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Inventory Healthy", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text("All medicines are currently above minimum threshold levels.", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(lowStockProducts) { item ->
                    LowStockProductCard(
                        product = item,
                        currency = settings.currency
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onNavigateToPurchases,
                colors = ButtonDefaults.buttonColors(containerColor = PharmacyTealPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Purchase Order for Low Stock")
            }
        }
    }
}

@Composable
fun LowStockProductCard(
    product: ProductWithStock,
    currency: String
) {
    val suggestedReorder = ((product.minStockLevel * 2) - product.totalStock).coerceAtLeast(product.minStockLevel)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(product.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text("${product.genericName} • ${product.category}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                StatusBadge(
                    text = "${product.totalStock} left",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Min Alert: ${product.minStockLevel} units", style = MaterialTheme.typography.labelSmall)
                Text(
                    text = "Suggested Reorder: $suggestedReorder units",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = PharmacyTealPrimary
                )
            }
            if (product.rackLocation.isNotEmpty()) {
                Text("Location: ${product.rackLocation}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}
