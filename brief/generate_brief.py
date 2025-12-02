from fpdf import FPDF

class PDF(FPDF):
    def header(self):
        self.set_font('Arial', 'B', 15)
        self.cell(0, 10, 'SS7 HA Gateway - Project Brief', 0, 1, 'C')
        self.ln(10)

    def footer(self):
        self.set_y(-15)
        self.set_font('Arial', 'I', 8)
        self.cell(0, 10, 'Page ' + str(self.page_no()) + '/{nb}', 0, 0, 'C')

    def chapter_title(self, title):
        self.set_font('Arial', 'B', 12)
        self.set_fill_color(200, 220, 255)
        self.cell(0, 6, title, 0, 1, 'L', 1)
        self.ln(4)

    def chapter_body(self, body):
        self.set_font('Arial', '', 11)
        self.multi_cell(0, 5, body)
        self.ln()

pdf = PDF()
pdf.alias_nb_pages()
pdf.add_page()

# Description
pdf.chapter_title('Project Description')
description = (
    "SS7 HA Gateway is an open-source, carrier-grade protocol handling layer designed for "
    "SS7/MAP/CAP networks. It serves as a bridge between legacy telecom infrastructure "
    "(HLR, MSC, VLR) and modern application ecosystems.\n\n"
    "The gateway provides high availability through distributed state management using NATS "
    "JetStream Key-Value Store and implements a low-latency event-driven architecture by "
    "publishing clean JSON events to NATS Core. This allows modern applications to interact "
    "with SS7 networks without needing complex SS7 stack knowledge or heavy middleware."
)
pdf.chapter_body(description)

# Target Audience
pdf.chapter_title('Who is this library for?')
audience = (
    "This library is designed for a wide range of stakeholders in the telecommunications industry:\n\n"
    "- Telecom Operators & Carriers: For modernizing legacy infrastructure and exposing services via modern APIs.\n"
    "- Mobile Virtual Network Operators (MVNOs): To implement core network services efficiently.\n"
    "- Value-Added Service (VAS) Providers: Companies building SMS Centers (SMSC), USSD gateways, or Location Based Services (LBS).\n"
    "- FinTech & Authentication Providers: For delivering OTPs and 2FA services via reliable SMS channels.\n"
    "- Software Developers: Who need to integrate with SS7 networks using familiar tools like NATS and JSON, avoiding the steep learning curve of SS7 protocols."
)
pdf.chapter_body(audience)

# Advantages
pdf.chapter_title('Key Advantages')
advantages = (
    "The SS7 HA Gateway offers several significant benefits:\n\n"
    "1. High Availability & Reliability:\n"
    "   Utilizes NATS JetStream Key-Value Store for distributed dialog state management. This ensures continuous operation even if a gateway node fails, as any other instance can retrieve the dialog state and continue the session. It guarantees seamless failover and data consistency.\n\n"
    "2. Simplified Architecture & Operations:\n"
    "   Replaces the complex combination of Apache Kafka and Redis with a single, lightweight NATS Server. This drastically reduces operational complexity and deployment footprint.\n\n"
    "3. Scalability & Load Balancing:\n"
    "   Built for horizontal scalability using NATS Queue Groups. Incoming SS7 traffic is automatically load-balanced across all available gateway instances. You can simply add more gateway nodes to handle increased load.\n\n"
    "4. Ultra-Low Latency Event-Driven Design:\n"
    "   Decouples the complex SS7 layer from business logic using NATS Pub/Sub. Applications consume standard JSON events with sub-millisecond latency.\n\n"
    "5. Cost-Effective & Open Source:\n"
    "   Reduces reliance on expensive proprietary hardware. It runs efficiently on standard commodity hardware or containerized environments (Docker/Kubernetes)."
)
pdf.chapter_body(advantages)

pdf.output('SS7_HA_Gateway_Brief_v2.pdf', 'F')
print("PDF generated successfully: SS7_HA_Gateway_Brief_v2.pdf")
